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

import com.facebook.buck.distributed.StampedeLocalBuildStatusEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.RemoteBuildRuleCompletionWaiter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import javax.annotation.Nullable;

/** Util class that contains orchestration code for racing build phase. */
public class RacingBuildPhase {
  private static final Logger LOG = Logger.get(RacingBuildPhase.class);

  /**
   * Races local build against distributed build, returning when either: - Local racing build
   * finishes before distributed build - 'Most build rules finished' event received - Distributed
   * build failed. -- If local fallback mode is enabled, waits for racing build to finish before
   * returning.
   *
   * @return True if all work completed during this phase and further phases not necessary
   * @throws InterruptedException
   */
  public static boolean run(
      DistBuildRunner distBuildRunner,
      LocalBuildRunner racingBuildRunner,
      RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter,
      boolean localBuildFallbackEnabled,
      BuckEventBus eventBus)
      throws InterruptedException {
    LOG.info("Starting racing build phase.");
    eventBus.post(new StampedeLocalBuildStatusEvent("racing"));

    racingBuildRunner.runLocalBuildAsync();
    attachMostBuildRulesCompletedCallback(racingBuildRunner, remoteBuildRuleCompletionWaiter);

    LOG.info("Waiting for racing build to finish, or most build rules event to be received..");
    racingBuildRunner.getBuildPhaseLatch().await();

    if (racingBuildRunner.isFinished()) {
      LOG.info("Local racing build finished before distributed build.");
      distBuildRunner.cancelAsLocalBuildFinished(
          racingBuildRunner.finishedSuccessfully(), racingBuildRunner.getExitCode());
      return true; // Build is done. Nothing more to do
    } else if (distBuildRunner.finishedSuccessfully()) {
      // Distributed build finished without a 'most build rules finished' event.
      // Technically this shouldn't happen, but handle it gracefully anyway.
      String message =
          "Distributed build finished before local racing build. Moving to synchronized build phase.";
      LOG.warn(message);
      racingBuildRunner.cancelAndWait(message);
      return false;
    } else if (distBuildRunner.failed()) {
      LOG.info("Distributed build failed before local racing build.");
      if (localBuildFallbackEnabled) {
        LOG.info("Falling back to local racing build. Waiting for it to finish..");
        racingBuildRunner.waitUntilFinished();
      } else {
        LOG.info("Killing local racing build as local fallback not enabled.");
        racingBuildRunner.cancelAndWait(
            "Distributed build failed, and local fallback not enabled.");
      }

      return true;
    } else if (remoteBuildRuleCompletionWaiter.waitForMostBuildRulesToFinishRemotely().isDone()) {
      // Important: this is the most generic case (as finishing a dist build will unlock this future
      // too),
      // so ensure that it comes last in the if/else clauses.
      String message =
          "Most build rules finished remotely before local racing build. Moving to synchronized build phase.";
      LOG.info(message);
      racingBuildRunner.cancelAndWait(message);
      return false;
    }

    LOG.error("Unsupported code path hit in racing build phase. Assuming it failed.");
    return false;
  }

  private static void attachMostBuildRulesCompletedCallback(
      LocalBuildRunner racingBuildExecutor,
      RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter) {
    Futures.addCallback(
        remoteBuildRuleCompletionWaiter.waitForMostBuildRulesToFinishRemotely(),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void result) {
            racingBuildExecutor.getBuildPhaseLatch().countDown();
          }

          @Override
          public void onFailure(Throwable t) {
            LOG.error(t, "Received exception in most build rules finished event handler.");
          }
        },
        MoreExecutors.directExecutor());
  }
}
