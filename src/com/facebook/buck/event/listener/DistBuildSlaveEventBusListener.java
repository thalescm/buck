/*
 * Copyright 2017-present Facebook, Inc.
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
package com.facebook.buck.event.listener;

import com.facebook.buck.artifact_cache.HttpArtifactCacheEvent;
import com.facebook.buck.distributed.DistBuildMode;
import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.DistBuildUtil;
import com.facebook.buck.distributed.FileMaterializationStatsTracker;
import com.facebook.buck.distributed.build_slave.BuildRuleFinishedPublisher;
import com.facebook.buck.distributed.build_slave.BuildSlaveTimingStatsTracker;
import com.facebook.buck.distributed.build_slave.HealthCheckStatsTracker;
import com.facebook.buck.distributed.build_slave.UnexpectedSlaveCacheMissTracker;
import com.facebook.buck.distributed.thrift.BuildSlaveConsoleEvent;
import com.facebook.buck.distributed.thrift.BuildSlaveFinishedStats;
import com.facebook.buck.distributed.thrift.BuildSlaveRunId;
import com.facebook.buck.distributed.thrift.BuildSlaveStatus;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.event.BuckEventListener;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.log.Logger;
import com.facebook.buck.log.TimedLogger;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.rules.BuildEvent;
import com.facebook.buck.rules.BuildRuleEvent;
import com.facebook.buck.util.network.hostname.HostnameFetching;
import com.facebook.buck.util.timing.Clock;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Listener to transmit DistBuildSlave events over to buck frontend. NOTE: We do not promise to
 * transmit every single update to BuildSlaveStatus, but we do promise to always transmit
 * BuildSlaveStatus with the latest updates.
 */
public class DistBuildSlaveEventBusListener
    implements BuildRuleFinishedPublisher,
        UnexpectedSlaveCacheMissTracker,
        BuckEventListener,
        Closeable {

  private static final TimedLogger LOG =
      new TimedLogger(Logger.get(DistBuildSlaveEventBusListener.class));

  private static final int DEFAULT_SERVER_UPDATE_PERIOD_MILLIS = 500;
  private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

  private final StampedeId stampedeId;
  private final BuildSlaveRunId buildSlaveRunId;
  private final Clock clock;
  private final ScheduledFuture<?> scheduledServerUpdates;
  private final ScheduledExecutorService networkScheduler;
  private final String hostname;

  private final Object consoleEventsLock = new Object();
  private final Object sendServerUpdatesLock = new Object();

  @GuardedBy("consoleEventsLock")
  private final List<BuildSlaveConsoleEvent> consoleEvents = new LinkedList<>();

  private final List<String> startedTargetsToSignal = new LinkedList<>();
  private final List<String> finishedTargetsToSignal = new LinkedList<>();

  private final CacheRateStatsKeeper cacheRateStatsKeeper = new CacheRateStatsKeeper();

  private volatile int ruleCount = 0;
  private final AtomicInteger buildRulesStartedCount = new AtomicInteger(0);
  private final AtomicInteger buildRulesFinishedCount = new AtomicInteger(0);
  private final AtomicInteger buildRulesSuccessCount = new AtomicInteger(0);
  private final AtomicInteger buildRulesFailureCount = new AtomicInteger(0);
  private final AtomicInteger totalBuildRuleFinishedEventsSent = new AtomicInteger(0);

  private final HttpCacheUploadStats httpCacheUploadStats = new HttpCacheUploadStats();

  private final FileMaterializationStatsTracker fileMaterializationStatsTracker;
  private final HealthCheckStatsTracker healthCheckStatsTracker;
  private final BuildSlaveTimingStatsTracker slaveStatsTracker;
  private final DistBuildMode distBuildMode;

  private volatile @Nullable DistBuildService distBuildService;
  private volatile Optional<Integer> exitCode = Optional.empty();
  private volatile boolean sentFinishedStatsToServer;

  public DistBuildSlaveEventBusListener(
      StampedeId stampedeId,
      BuildSlaveRunId buildSlaveRunId,
      DistBuildMode distBuildMode,
      Clock clock,
      BuildSlaveTimingStatsTracker slaveStatsTracker,
      FileMaterializationStatsTracker fileMaterializationStatsTracker,
      HealthCheckStatsTracker healthCheckStatsTracker,
      ScheduledExecutorService networkScheduler) {
    this(
        stampedeId,
        buildSlaveRunId,
        distBuildMode,
        clock,
        slaveStatsTracker,
        fileMaterializationStatsTracker,
        healthCheckStatsTracker,
        networkScheduler,
        DEFAULT_SERVER_UPDATE_PERIOD_MILLIS);
  }

  public DistBuildSlaveEventBusListener(
      StampedeId stampedeId,
      BuildSlaveRunId runId,
      DistBuildMode distBuildMode,
      Clock clock,
      BuildSlaveTimingStatsTracker slaveStatsTracker,
      FileMaterializationStatsTracker fileMaterializationStatsTracker,
      HealthCheckStatsTracker healthCheckStatsTracker,
      ScheduledExecutorService networkScheduler,
      long serverUpdatePeriodMillis) {
    this.stampedeId = stampedeId;
    this.buildSlaveRunId = runId;
    this.clock = clock;
    this.slaveStatsTracker = slaveStatsTracker;
    this.fileMaterializationStatsTracker = fileMaterializationStatsTracker;
    this.healthCheckStatsTracker = healthCheckStatsTracker;
    this.networkScheduler = networkScheduler;
    this.distBuildMode = distBuildMode;

    scheduledServerUpdates =
        networkScheduler.scheduleAtFixedRate(
            this::sendServerUpdates, 0, serverUpdatePeriodMillis, TimeUnit.MILLISECONDS);

    String hostname;
    try {
      hostname = HostnameFetching.getHostname();
    } catch (IOException e) {
      hostname = "unknown";
    }
    this.hostname = hostname;
  }

  public void setDistBuildService(DistBuildService service) {
    this.distBuildService = service;
  }

  @Override
  public void outputTrace(BuildId buildId) throws InterruptedException {}

  @Override
  public void close() throws IOException {
    if (scheduledServerUpdates.isCancelled()) {
      return; // close() has already been called. Cancelling again will fail.
    }

    boolean cancelled = scheduledServerUpdates.cancel(false);

    if (!cancelled) {
      // Wait for the timer to shut down.
      try {
        scheduledServerUpdates.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        LOG.error(e);
      } catch (CancellationException e) {
        LOG.info("Failed to call get() on scheduled executor future, as already cancelled.");
      }
    }

    // Send final updates.
    sendServerUpdates();
    if (exitCode.isPresent()) {
      sendFinishedStatsToFrontend(createBuildSlaveFinishedStats());
    }
  }

  private BuildSlaveStatus createBuildSlaveStatus() {
    return new BuildSlaveStatus()
        .setStampedeId(stampedeId)
        .setBuildSlaveRunId(buildSlaveRunId)
        .setTotalRulesCount(ruleCount)
        .setRulesStartedCount(buildRulesStartedCount.get())
        .setRulesFinishedCount(buildRulesFinishedCount.get())
        .setRulesSuccessCount(buildRulesSuccessCount.get())
        .setRulesFailureCount(buildRulesFailureCount.get())
        .setCacheRateStats(cacheRateStatsKeeper.getSerializableStats())
        .setHttpArtifactTotalBytesUploaded(httpCacheUploadStats.getHttpArtifactTotalBytesUploaded())
        .setHttpArtifactUploadsScheduledCount(
            httpCacheUploadStats.getHttpArtifactTotalUploadsScheduledCount())
        .setHttpArtifactUploadsOngoingCount(
            httpCacheUploadStats.getHttpArtifactUploadsOngoingCount())
        .setHttpArtifactUploadsSuccessCount(
            httpCacheUploadStats.getHttpArtifactUploadsSuccessCount())
        .setHttpArtifactUploadsFailureCount(
            httpCacheUploadStats.getHttpArtifactUploadsFailureCount())
        .setFilesMaterializedCount(
            fileMaterializationStatsTracker.getTotalFilesMaterializedCount());
  }

  private BuildSlaveFinishedStats createBuildSlaveFinishedStats() {
    BuildSlaveFinishedStats finishedStats =
        new BuildSlaveFinishedStats()
            .setHostname(hostname)
            .setDistBuildMode(distBuildMode.name())
            .setBuildSlaveStatus(createBuildSlaveStatus())
            .setFileMaterializationStats(
                fileMaterializationStatsTracker.getFileMaterializationStats())
            .setHealthCheckStats(healthCheckStatsTracker.getHealthCheckStats())
            .setBuildSlavePerStageTimingStats(slaveStatsTracker.generateStats());
    Preconditions.checkState(
        exitCode.isPresent(),
        "BuildSlaveFinishedStats can only be generated after we are finished building.");
    finishedStats.setExitCode(exitCode.get());
    return finishedStats;
  }

  private synchronized void sendFinishedStatsToFrontend(BuildSlaveFinishedStats finishedStats) {
    if (distBuildService == null || sentFinishedStatsToServer) {
      return;
    }

    try {
      distBuildService.storeBuildSlaveFinishedStats(stampedeId, buildSlaveRunId, finishedStats);
      sentFinishedStatsToServer = true;
    } catch (IOException e) {
      LOG.error(e, "Could not update slave status to frontend.");
    }
  }

  private void sendStatusToFrontend() {
    if (distBuildService == null) {
      return;
    }

    try {
      distBuildService.updateBuildSlaveStatus(
          stampedeId, buildSlaveRunId, createBuildSlaveStatus());
    } catch (IOException e) {
      LOG.error(e, "Could not update slave status to frontend.");
    }
  }

  private void sendBuildRuleCompletedEvents() {
    if (distBuildService == null) {
      return;
    }

    ImmutableList<String> finishedTargetsCopy;
    synchronized (finishedTargetsToSignal) {
      finishedTargetsCopy = ImmutableList.copyOf(finishedTargetsToSignal);
    }

    if (finishedTargetsCopy.size() == 0) {
      return;
    }

    for (String target : finishedTargetsCopy) {
      LOG.info(String.format("Publishing build rule finished event for target [%s]", target));
    }

    // TODO: consider batching if list is too big.
    try {
      distBuildService.uploadBuildRuleFinishedEvents(
          stampedeId, buildSlaveRunId, finishedTargetsCopy);

      totalBuildRuleFinishedEventsSent.addAndGet(finishedTargetsCopy.size());
    } catch (IOException e) {
      LOG.error(e, "Could not upload build rule finished events to frontend.");
    }

    // Only remove events once we are sure they have been sent to the client, otherwise
    // client will freeze up waiting for targets until end of the build.
    synchronized (finishedTargetsToSignal) {
      finishedTargetsToSignal.removeAll(finishedTargetsCopy);
    }
  }

  private void sendBuildRuleStartedEvents() {
    if (distBuildService == null) {
      return;
    }

    ImmutableList<String> startedTargetsCopy;
    synchronized (startedTargetsToSignal) {
      startedTargetsCopy = ImmutableList.copyOf(startedTargetsToSignal);
    }

    if (startedTargetsCopy.size() == 0) {
      return;
    }

    for (String target : startedTargetsCopy) {
      LOG.info(String.format("Publishing build rule started event for target [%s]", target));
    }

    try {
      distBuildService.uploadBuildRuleStartedEvents(
          stampedeId, buildSlaveRunId, startedTargetsCopy);
    } catch (IOException e) {
      LOG.error(e, "Could not upload build rule started events to frontend.");
    }

    // Only remove events once we are sure they have been sent to the client, otherwise
    // client will freeze up waiting for targets until end of the build.
    synchronized (startedTargetsToSignal) {
      startedTargetsToSignal.removeAll(startedTargetsCopy);
    }
  }

  private void sendConsoleEventsToFrontend() {
    if (distBuildService == null) {
      return;
    }

    ImmutableList<BuildSlaveConsoleEvent> consoleEventsCopy;
    synchronized (consoleEventsLock) {
      consoleEventsCopy = ImmutableList.copyOf(consoleEvents);
    }

    if (consoleEventsCopy.size() == 0) {
      return;
    }

    try {
      distBuildService.uploadBuildSlaveConsoleEvents(
          stampedeId, buildSlaveRunId, consoleEventsCopy);
    } catch (IOException e) {
      LOG.error(e, "Could not upload slave console events to frontend.");
    }

    synchronized (consoleEventsLock) {
      consoleEvents.removeAll(consoleEventsCopy);
    }
  }

  private void sendServerUpdates() {
    synchronized (sendServerUpdatesLock) {
      try {
        LOG.info("Sending server updates..");
        sendStatusToFrontend();
        sendConsoleEventsToFrontend();
        sendBuildRuleStartedEvents();
        sendBuildRuleCompletedEvents();
      } catch (RuntimeException ex) {
        LOG.error(ex, "Failed to send slave server updates.");
      }
    }
  }

  /** Publishes events from slave back to client that kicked off build (via frontend) */
  public void sendFinalServerUpdates() {
    synchronized (sendServerUpdatesLock) {
      sendServerUpdates();
      if (totalBuildRuleFinishedEventsSent.get() == 0) {
        return; // This was not the co-ordiantor.
      }
      try {
        if (distBuildService != null) {
          distBuildService.sendAllBuildRulesPublishedEvent(stampedeId, buildSlaveRunId);
        }

      } catch (RuntimeException | IOException e) {
        LOG.error(e, "Failed to send slave final server updates.");
      }
    }
  }

  public void publishBuildSlaveFinishedEvent(int exitCode) {
    this.exitCode = Optional.of(exitCode);
    BuildSlaveFinishedStats finishedStats = createBuildSlaveFinishedStats();
    networkScheduler.schedule(
        () -> sendFinishedStatsToFrontend(finishedStats), 0, TimeUnit.SECONDS);
  }

  /** Record unexpected cache misses in build slaves. */
  @Override
  public void onUnexpectedCacheMiss(int numUnexpectedMisses) {
    cacheRateStatsKeeper.recordUnexpectedCacheMisses(numUnexpectedMisses);
  }

  @Subscribe
  public void logEvent(ConsoleEvent event) {
    if (!event.getLevel().equals(Level.WARNING) && !event.getLevel().equals(Level.SEVERE)) {
      return;
    }
    synchronized (consoleEventsLock) {
      BuildSlaveConsoleEvent slaveConsoleEvent =
          DistBuildUtil.createBuildSlaveConsoleEvent(event, clock.currentTimeMillis());
      consoleEvents.add(slaveConsoleEvent);
    }
  }

  @Subscribe
  public void ruleCountCalculated(BuildEvent.RuleCountCalculated calculated) {
    cacheRateStatsKeeper.ruleCountCalculated(calculated);
    ruleCount = calculated.getNumRules();
  }

  @Subscribe
  public void ruleCountUpdated(BuildEvent.UnskippedRuleCountUpdated updated) {
    cacheRateStatsKeeper.ruleCountUpdated(updated);
    ruleCount = updated.getNumRules();
  }

  @SuppressWarnings("unused")
  @Subscribe
  public void buildRuleStarted(BuildRuleEvent.Started started) {
    buildRulesStartedCount.incrementAndGet();
  }

  @Subscribe
  public void buildRuleFinished(BuildRuleEvent.Finished finished) {
    cacheRateStatsKeeper.buildRuleFinished(finished);
    buildRulesStartedCount.decrementAndGet();
    buildRulesFinishedCount.incrementAndGet();

    switch (finished.getStatus()) {
      case SUCCESS:
        buildRulesSuccessCount.incrementAndGet();
        break;
      case FAIL:
        buildRulesFailureCount.incrementAndGet();
        break;
      case CANCELED:
        break;
    }
  }

  @SuppressWarnings("unused")
  @Subscribe
  public void buildRuleResumed(BuildRuleEvent.Resumed resumed) {
    buildRulesStartedCount.incrementAndGet();
  }

  @SuppressWarnings("unused")
  @Subscribe
  public void buildRuleSuspended(BuildRuleEvent.Suspended suspended) {
    buildRulesStartedCount.decrementAndGet();
  }

  @Subscribe
  public void onHttpArtifactCacheScheduledEvent(HttpArtifactCacheEvent.Scheduled event) {
    httpCacheUploadStats.processHttpArtifactCacheScheduledEvent(event);
  }

  @Subscribe
  public void onHttpArtifactCacheStartedEvent(HttpArtifactCacheEvent.Started event) {
    httpCacheUploadStats.processHttpArtifactCacheStartedEvent(event);
  }

  @Subscribe
  public void onHttpArtifactCacheFinishedEvent(HttpArtifactCacheEvent.Finished event) {
    httpCacheUploadStats.processHttpArtifactCacheFinishedEvent(event);
  }

  @Override
  public void createBuildRuleStartedEvents(ImmutableList<String> startedTargets) {
    if (startedTargets.size() == 0) {
      return;
    }
    for (String target : startedTargets) {
      LOG.info(String.format("Queueing build rule started event for target [%s]", target));
    }
    synchronized (startedTargetsToSignal) {
      startedTargetsToSignal.addAll(startedTargets);
    }
  }

  @Override
  public void createBuildRuleCompletionEvents(ImmutableList<String> finishedTargets) {
    if (finishedTargets.size() == 0) {
      return;
    }
    for (String target : finishedTargets) {
      LOG.info(String.format("Queueing build rule finished event for target [%s]", target));
    }
    synchronized (finishedTargetsToSignal) {
      finishedTargetsToSignal.addAll(finishedTargets);
    }
  }
}
