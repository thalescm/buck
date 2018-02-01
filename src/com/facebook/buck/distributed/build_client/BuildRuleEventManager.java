/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.distributed.build_client;

import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.RemoteBuildRuleCompletionNotifier;
import com.facebook.buck.util.timing.Clock;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

/** Handles build rule events sent by distributed build workers */
public class BuildRuleEventManager {
  private static final Logger LOG = Logger.get(BuildRuleEventManager.class);

  private final RemoteBuildRuleCompletionNotifier remoteBuildRuleCompletionNotifier;
  private final Clock clock;
  private final int cacheSynchronizationSafetyMarginMillis;

  @GuardedBy("this")
  private final List<TimestampedBuildRuleFinishedEvent> pendingBuildRuleFinishedEvent =
      new ArrayList<>();

  private volatile boolean allBuildRulesFinishedEventReceived = false;

  public BuildRuleEventManager(
      RemoteBuildRuleCompletionNotifier remoteBuildRuleCompletionNotifier,
      Clock clock,
      int cacheSynchronizationSafetyMarginMillis) {
    this.remoteBuildRuleCompletionNotifier = remoteBuildRuleCompletionNotifier;
    this.clock = clock;
    this.cacheSynchronizationSafetyMarginMillis = cacheSynchronizationSafetyMarginMillis;
  }

  /**
   * Records the receipt of a BuildRuleStarted event
   *
   * @param buildTarget
   */
  public void recordBuildRuleStartedEvent(String buildTarget) {
    LOG.debug(String.format("Received BUILD_RULE_STARTED_EVENT for [%s].", buildTarget));
    remoteBuildRuleCompletionNotifier.signalStartedRemoteBuildingOfBuildRule(buildTarget);
  }

  /**
   * Records the receipt of a BuildRuleFinishedEvent
   *
   * @param timestampMillis
   * @param buildTarget
   */
  public synchronized void recordBuildRuleFinishedEvent(long timestampMillis, String buildTarget) {
    LOG.debug(
        String.format(
            "Received BUILD_RULE_FINISHED_EVENT for [%s]. Queueing for dispatch.", buildTarget));

    pendingBuildRuleFinishedEvent.add(
        new TimestampedBuildRuleFinishedEvent(timestampMillis, buildTarget));
  }

  /** Records the receipt of a ALL_BUILD_RULES_FINISHED_EVENT */
  public synchronized void recordAllBuildRulesFinishedEvent() {
    LOG.info("Received ALL_BUILD_RULES_FINISHED_EVENT");
    allBuildRulesFinishedEventReceived = true;
  }

  /** @return true if ALL_BUILD_RULES_FINISHED_EVENT received. */
  public synchronized boolean allBuildRulesFinishedEventReceived() {
    return allBuildRulesFinishedEventReceived;
  }

  public synchronized void mostBuildRulesFinishedEventReceived() {
    LOG.info("Received MOST_BUILD_RULES_FINISHED_EVENT");
    remoteBuildRuleCompletionNotifier.signalMostBuildRulesFinished();
  }

  /**
   * Publishes results of all pending BuildRuleFinishedEvent, waiting for cache synchronization if
   * necessary.
   */
  public void flushAllPendingBuildRuleFinishedEvents() {
    if (!allBuildRulesFinishedEventReceived) {
      LOG.warn(
          "flushAllPendingBuildRuleFinishedEvents(..) called without first receiving ALL_BUILD_RULES_FINISHED_EVENT");
    }

    if (pendingBuildRuleFinishedEvent.size() == 0) {
      return;
    }

    List<TimestampedBuildRuleFinishedEvent> eventsToPublish = Lists.newArrayList();
    synchronized (this) {
      eventsToPublish.addAll(pendingBuildRuleFinishedEvent);
      pendingBuildRuleFinishedEvent.clear();
    }

    for (TimestampedBuildRuleFinishedEvent event : eventsToPublish) {
      long currentTimeMillis = clock.currentTimeMillis();
      long elapsedMillisSinceEvent = currentTimeMillis - event.eventTimestampMillis;
      if (elapsedMillisSinceEvent < cacheSynchronizationSafetyMarginMillis) {
        try {
          long millisUntilCacheSynchronization =
              cacheSynchronizationSafetyMarginMillis - elapsedMillisSinceEvent;
          threadSleep(millisUntilCacheSynchronization);
        } catch (InterruptedException e) {
          LOG.error(
              e,
              "InterruptedException thrown while publishing build finished events. Skipping remaining events.");
          Thread.currentThread().interrupt();
          return;
        }
      }

      remoteBuildRuleCompletionNotifier.signalCompletionOfBuildRule(event.buildTarget);
    }
  }

  /**
   * Publishes results of pending BuildRuleFinishedEvent that have already synchronized with cache.
   */
  public void publishCacheSynchronizedBuildRuleFinishedEvents() {
    if (pendingBuildRuleFinishedEvent.size() == 0) {
      return;
    }

    List<TimestampedBuildRuleFinishedEvent> eventsToPublish = Lists.newArrayList();
    synchronized (this) {
      findAllCacheSynchronizedBuildRules(eventsToPublish);
      pendingBuildRuleFinishedEvent.removeAll(eventsToPublish);
    }

    for (TimestampedBuildRuleFinishedEvent event : eventsToPublish) {
      remoteBuildRuleCompletionNotifier.signalCompletionOfBuildRule(event.buildTarget);
    }
  }

  @VisibleForTesting
  protected void threadSleep(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }

  private void findAllCacheSynchronizedBuildRules(
      List<TimestampedBuildRuleFinishedEvent> eventsToPublish) {
    long currentTimeMillis = clock.currentTimeMillis();
    for (TimestampedBuildRuleFinishedEvent event : pendingBuildRuleFinishedEvent) {
      long elapsedMillisSinceEvent = currentTimeMillis - event.eventTimestampMillis;

      if (elapsedMillisSinceEvent >= cacheSynchronizationSafetyMarginMillis) {
        eventsToPublish.add(event);
      } else {
        // Events are time ordered, so if this event isn't synchronized, nothing else is either
        break;
      }
    }
  }

  private class TimestampedBuildRuleFinishedEvent {
    private long eventTimestampMillis;
    private String buildTarget;

    public TimestampedBuildRuleFinishedEvent(long eventTimestampMillis, String buildTarget) {
      this.eventTimestampMillis = eventTimestampMillis;
      this.buildTarget = buildTarget;
    }
  }
}
