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

/*
 * Copyright 2018-present Facebook; Inc.
 *
 * Licensed under the Apache License; Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing; software
 * distributed under the License is distributed on an "AS IS" BASIS; WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND; either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.distributed.build_client;

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.command.Build;
import com.facebook.buck.command.LocalBuildExecutorInvoker;
import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.ExitCode;
import com.facebook.buck.distributed.thrift.BuildStatus;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.rules.BuildEvent;
import com.facebook.buck.rules.RemoteBuildRuleCompletionWaiter;
import com.facebook.buck.rules.RemoteBuildRuleSynchronizer;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class StampedeBuildClientTest {
  private static final StampedeId INITIALIZED_STAMPEDE_ID = createStampedeId("id_one");

  private static final int REMOTE_FAILURE_CODE =
      ExitCode.DISTRIBUTED_BUILD_STEP_REMOTE_FAILURE.getCode();
  private static final int LOCAL_BUILD_FINISHED_FIRST_EXIT_CODE =
      ExitCode.LOCAL_BUILD_FINISHED_FIRST.getCode();
  private static final int NON_SUCCESS_EXIT_CODE = 50;
  private static final int NON_SUCCESS_EXIT_CODE_TWO = 51;
  private static final int SUCCESS_CODE = 0;
  private static final boolean ONE_STAGE = true;
  private static final boolean TWO_STAGE = false;
  private static final boolean NO_FALLBACK = false;
  private static final boolean FALLBACK_ENABLED = true;

  private BuckEventBus mockEventBus;
  private RemoteBuildRuleSynchronizer remoteBuildRuleSynchronizer;
  private ExecutorService executorForLocalBuild;
  private ExecutorService executorForDistBuildController;
  private DistBuildService mockDistBuildService;
  private BuildEvent.DistBuildStarted distBuildStartedEvent;
  private CountDownLatch localBuildExecutorInvokerPhaseOneLatch;
  private CountDownLatch localBuildExecutorInvokerPhaseTwoLatch;
  private LocalBuildExecutorInvoker guardedLocalBuildExecutorInvoker;
  private LocalBuildExecutorInvoker mockLocalBuildExecutorInvoker;
  private CountDownLatch distBuildControllerInvokerLatch;
  private DistBuildControllerInvoker guardedDistBuildControllerInvoker;
  private DistBuildControllerInvoker mockDistBuildControllerInvoker;
  private StampedeBuildClient buildClient;
  private ExecutorService buildClientExecutor;
  private Build buildOneMock;
  private Build buildTwoMock;
  private CountDownLatch waitForRacingBuildCalledLatch;
  private CountDownLatch waitForSynchronizedBuildCalledLatch;
  private boolean waitGracefullyForDistributedBuildThreadToFinish;
  private Optional<StampedeId> stampedeId = Optional.empty();

  @Before
  public void setUp() {
    mockEventBus = EasyMock.createMock(BuckEventBus.class);
    remoteBuildRuleSynchronizer = new RemoteBuildRuleSynchronizer();
    executorForLocalBuild = Executors.newSingleThreadExecutor();
    executorForDistBuildController = Executors.newSingleThreadExecutor();
    mockDistBuildService = EasyMock.createMock(DistBuildService.class);
    distBuildStartedEvent = BuildEvent.distBuildStarted();
    mockLocalBuildExecutorInvoker = EasyMock.createMock(LocalBuildExecutorInvoker.class);
    localBuildExecutorInvokerPhaseOneLatch = new CountDownLatch(1);
    localBuildExecutorInvokerPhaseTwoLatch = new CountDownLatch(1);
    buildOneMock = EasyMock.createMock(Build.class);
    buildTwoMock = EasyMock.createMock(Build.class);
    AtomicBoolean isFirstBuild = new AtomicBoolean(true);
    guardedLocalBuildExecutorInvoker =
        new LocalBuildExecutorInvoker() {
          @Override
          public int executeLocalBuild(
              boolean isDownloadHeavyBuild,
              RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter,
              CountDownLatch initializeBuildLatch,
              AtomicReference<Build> buildReference)
              throws IOException, InterruptedException {

            // First simulate initializing the build
            boolean wasFirstBuild = isFirstBuild.compareAndSet(true, false);
            if (wasFirstBuild) {
              buildReference.set(
                  buildOneMock); // Racing build, or synchronized build if single stage
            } else {
              buildReference.set(buildTwoMock); // Always synchronized build
            }
            initializeBuildLatch.countDown(); // Build reference has been set

            // Now wait for test to signal that the mock should be invoked and return an exit code.
            if (wasFirstBuild) {
              localBuildExecutorInvokerPhaseOneLatch.await();
            } else {
              localBuildExecutorInvokerPhaseTwoLatch.await();
            }

            return mockLocalBuildExecutorInvoker.executeLocalBuild(
                isDownloadHeavyBuild,
                remoteBuildRuleCompletionWaiter,
                initializeBuildLatch,
                buildReference);
          }
        };
    mockDistBuildControllerInvoker = EasyMock.createMock(DistBuildControllerInvoker.class);
    distBuildControllerInvokerLatch = new CountDownLatch(1);
    guardedDistBuildControllerInvoker =
        () -> {
          distBuildControllerInvokerLatch.await();
          return mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode();
        };
    waitForRacingBuildCalledLatch = new CountDownLatch(1);
    waitForSynchronizedBuildCalledLatch = new CountDownLatch(1);
    waitGracefullyForDistributedBuildThreadToFinish = false;
    createStampedBuildClient();
    buildClientExecutor = Executors.newSingleThreadExecutor();
  }

  private void createStampedBuildClient(StampedeId stampedeId) {
    this.stampedeId = Optional.of(stampedeId);
    createStampedBuildClient();
  }

  private void createStampedBuildClient() {
    this.buildClient =
        new StampedeBuildClient(
            mockEventBus,
            remoteBuildRuleSynchronizer,
            executorForLocalBuild,
            executorForDistBuildController,
            mockDistBuildService,
            distBuildStartedEvent,
            waitForRacingBuildCalledLatch,
            waitForSynchronizedBuildCalledLatch,
            guardedLocalBuildExecutorInvoker,
            guardedDistBuildControllerInvoker,
            waitGracefullyForDistributedBuildThreadToFinish,
            stampedeId);
  }

  @Test
  public void synchronizedBuildCompletesAfterDistBuildFailsForSinglePhaseBuildWithFallback()
      throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // One phase. Fallback enabled. Racing build is skipped.
    // Distributed build fails, and then falls back to synchronized local build which completes

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    // Simulate failure at a remote minion
    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode())
        .andReturn(REMOTE_FAILURE_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(ONE_STAGE, FALLBACK_ENABLED));

    // Simulate most build rules finished event being received.
    distBuildControllerInvokerLatch.countDown(); // distributed build fails
    waitForSynchronizedBuildCalledLatch.await(); // ensure waitUntilFinished(..) called on build
    localBuildExecutorInvokerPhaseOneLatch.countDown(); // allow synchronized build to complete

    assertLocalAndDistributedExitCodes(buildClientFuture, SUCCESS_CODE, REMOTE_FAILURE_CODE);
    verifyAllMocks();
  }

  @Test
  public void synchronizedBuildCompletesAfterDistBuildCompletesForSinglePhase()
      throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // One phase. Fallback enabled. Racing build is skipped.
    // Distributed build completes, and then synchronized local build completes

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode()).andReturn(SUCCESS_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(ONE_STAGE, FALLBACK_ENABLED));

    // Simulate most build rules finished event being received.
    distBuildControllerInvokerLatch.countDown(); // distributed build succeeds
    waitForSynchronizedBuildCalledLatch.await(); // ensure waitUntilFinished(..) called on build
    localBuildExecutorInvokerPhaseOneLatch.countDown(); // allow synchronized build to complete

    assertLocalAndDistributedExitCodes(buildClientFuture, SUCCESS_CODE, SUCCESS_CODE);
    verifyAllMocks();
  }

  @Test
  public void
      racerBuildKilledWhenMostBuildRulesFinishedThenFallsBackToSynchronizedBuildWhenDistBuildFails()
          throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Two phases enabled. Fallback enabled.
    // During local racing build phase a 'most build rules finished' event is received.
    // Racing build is cancelled, and build moves to local synchronized phase.
    // Distributed build fails and then falls back to local synchronized build.

    // Racing build should be cancelled when most build rules finished event received
    ensureTerminationOfBuild(buildOneMock, localBuildExecutorInvokerPhaseOneLatch);

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(NON_SUCCESS_EXIT_CODE);

    // Simulate failure at a remote minion
    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode())
        .andReturn(REMOTE_FAILURE_CODE);

    // Ensure local synchronized build is invoked and finishes with failure code (as it was
    // terminated)
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, FALLBACK_ENABLED));

    // Simulate most build rules finished event being received.
    remoteBuildRuleSynchronizer.signalMostBuildRulesFinished();
    waitForRacingBuildCalledLatch.await(); // waitUntilFinished(..) called on racing build
    distBuildControllerInvokerLatch.countDown(); // distributed build fails
    waitForSynchronizedBuildCalledLatch.await(); // waitUntilFinished(..) called on sync build
    localBuildExecutorInvokerPhaseTwoLatch.countDown(); // allow synchronized build to complete

    assertLocalAndDistributedExitCodes(buildClientFuture, SUCCESS_CODE, REMOTE_FAILURE_CODE);
    verifyAllMocks();
  }

  @Test
  public void
      racerBuildKilledWhenMostBuildRulesFinishedThenSynchronizedBuildKilledWhenDistBuildFails()
          throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Two phases enabled. No fallback.
    // During local racing build phase a 'most build rules finished' event is received.
    // Racing build is cancelled, and build moves to local synchronized phase.
    // Distributed build fails and so local synchronized build is killed as no fallback.

    // Racing build should be cancelled when most build rules finished event received
    ensureTerminationOfBuild(buildOneMock, localBuildExecutorInvokerPhaseOneLatch);

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(NON_SUCCESS_EXIT_CODE);

    // Simulate failure at a remote minion
    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode())
        .andReturn(REMOTE_FAILURE_CODE);

    // Synchronized build should be cancelled when distributed build fails
    ensureTerminationOfBuild(buildTwoMock, localBuildExecutorInvokerPhaseTwoLatch);

    // Ensure local synchronized build is invoked and finishes with failure code (as it was
    // terminated)
    expectMockLocalBuildExecutorReturnsWithCode(NON_SUCCESS_EXIT_CODE_TWO);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, NO_FALLBACK));

    // Simulate most build rules finished event being received.
    remoteBuildRuleSynchronizer.signalMostBuildRulesFinished();
    waitForRacingBuildCalledLatch.await(); // waitUntilFinished(..) called on racing build
    distBuildControllerInvokerLatch.countDown(); // distributed build fails
    waitForSynchronizedBuildCalledLatch.await(); // waitUntilFinished(..) called on sync build

    assertLocalAndDistributedExitCodes(
        buildClientFuture, NON_SUCCESS_EXIT_CODE_TWO, REMOTE_FAILURE_CODE);
    verifyAllMocks();
  }

  @Test
  public void
      racerBuildKilledWhenMostBuildRulesFinishedThenDistBuildKilledWhenSynchronizedBuildWins()
          throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Two phases enabled. No fallback.
    // During local racing build phase a 'most build rules finished' event is received.
    // Racing build is cancelled, and build moves to local synchronized phase.
    // Synchronized build wins and then distributed build is killed.
    createStampedBuildClient(INITIALIZED_STAMPEDE_ID);

    // Racing build should be cancelled when most build rules finished event received
    ensureTerminationOfBuild(buildOneMock, localBuildExecutorInvokerPhaseOneLatch);

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    // Note: we always wait for racing build thread to shut down cleanly.
    expectMockLocalBuildExecutorReturnsWithCode(NON_SUCCESS_EXIT_CODE);

    // Distributed build job should be set to finished when synchronized build completes
    mockDistBuildService.setFinalBuildStatus(
        INITIALIZED_STAMPEDE_ID,
        BuildStatus.FINISHED_SUCCESSFULLY,
        "succeeded locally before distributed build finished.");
    EasyMock.expectLastCall();

    // Synchronized build returns with success code
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, NO_FALLBACK));

    // Simulate most build rules finished event being received.
    remoteBuildRuleSynchronizer.signalMostBuildRulesFinished();
    waitForRacingBuildCalledLatch.await(); // waitUntilFinished(..) called on racing build
    localBuildExecutorInvokerPhaseTwoLatch.countDown(); // Local synchronized build completes

    assertLocalAndDistributedExitCodes(
        buildClientFuture, SUCCESS_CODE, LOCAL_BUILD_FINISHED_FIRST_EXIT_CODE);
    verifyAllMocks();
  }

  @Test
  public void
      racerBuildKilledWhenMostBuildRulesFinishedThenWaitsForSynchronizedBuildWhenDistBuildCompletes()
          throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Two phases enabled. No fallback. 'Most build rules finished' event received during racing.
    // Racing build is cancelled, and build moves to local synchronized phase.
    // Distributed build finishes, and then waits for local build to finish.

    // Racing build should be cancelled when most build rules finished event received
    ensureTerminationOfBuild(buildOneMock, localBuildExecutorInvokerPhaseOneLatch);

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(NON_SUCCESS_EXIT_CODE);

    // Distributed build finishes successfully
    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode()).andReturn(SUCCESS_CODE);

    // Synchronized build returns with success code
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, NO_FALLBACK));

    // Simulate most build rules finished event being received.
    remoteBuildRuleSynchronizer.signalMostBuildRulesFinished();

    waitForRacingBuildCalledLatch.await(); // waitUntilFinished(..) called on racing build
    distBuildControllerInvokerLatch.countDown(); // Distributed build completes
    waitForSynchronizedBuildCalledLatch.await(); // waitUntilFinished(..) called on sync build
    localBuildExecutorInvokerPhaseTwoLatch.countDown(); // Local synchronized build completes

    assertLocalAndDistributedExitCodes(buildClientFuture, SUCCESS_CODE, SUCCESS_CODE);
    verifyAllMocks();
  }

  @Test
  public void racerBuildWinsAndThenDistributedBuildKilled()
      throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Two phases enabled. No fallback. Local racing build finishes before anything else.
    // Distributed build is still pending, so it's killed, and racing build exit code returned.
    createStampedBuildClient(INITIALIZED_STAMPEDE_ID);

    // Ensure local racing build is invoked and finishes with success code
    localBuildExecutorInvokerPhaseOneLatch.countDown();
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    mockDistBuildService.setFinalBuildStatus(
        INITIALIZED_STAMPEDE_ID,
        BuildStatus.FINISHED_SUCCESSFULLY,
        "succeeded locally before distributed build finished.");
    EasyMock.expectLastCall();

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, NO_FALLBACK));

    assertLocalAndDistributedExitCodes(
        buildClientFuture, SUCCESS_CODE, LOCAL_BUILD_FINISHED_FIRST_EXIT_CODE);
    verifyAllMocks();
  }

  @Test
  public void remoteBuildFailsAndThenRacingBuildKilledAsNoLocalFallback()
      throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Two phases. No fallback. Remote build fails during racing build phase.
    // Local fallback *is not* enabled, so build client kills the racing build and returns.

    // Simulate failure at a remote minion
    distBuildControllerInvokerLatch.countDown();
    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode())
        .andReturn(REMOTE_FAILURE_CODE);

    // Ensure Build object for racing build is terminated and then unlock local build executor
    ensureTerminationOfBuild(buildOneMock, localBuildExecutorInvokerPhaseOneLatch);

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(NON_SUCCESS_EXIT_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, NO_FALLBACK));

    assertLocalAndDistributedExitCodes(
        buildClientFuture, NON_SUCCESS_EXIT_CODE, REMOTE_FAILURE_CODE);
    verifyAllMocks();
  }

  @Test
  public void remoteBuildFailsAndThenWaitsForRacingBuildToCompleteAsFallbackEnabled()
      throws InterruptedException, IOException, ExecutionException {
    // Summary:
    // Remote build fails during racing build phase, before racing build has finished.
    // Local fallback *is* enabled, so racing build keeps going until completion.

    // Simulate failure at a remote minion
    distBuildControllerInvokerLatch.countDown();
    expect(mockDistBuildControllerInvoker.runDistBuildAndReturnExitCode())
        .andReturn(REMOTE_FAILURE_CODE);

    // Ensure local racing build is invoked and finishes with failure code (as it was terminated)
    expectMockLocalBuildExecutorReturnsWithCode(SUCCESS_CODE);

    replayAllMocks();

    // Run the build client in another thread
    Future<Integer> buildClientFuture =
        buildClientExecutor.submit(() -> buildClient.build(TWO_STAGE, FALLBACK_ENABLED));

    // Only let local build runner finish once we are sure distributed build thread is dead,
    // and waitUntilFinished(..) called on local racing build runner
    waitForRacingBuildCalledLatch.await();
    localBuildExecutorInvokerPhaseOneLatch.countDown();

    assertLocalAndDistributedExitCodes(buildClientFuture, SUCCESS_CODE, REMOTE_FAILURE_CODE);
    verifyAllMocks();
  }

  private static StampedeId createStampedeId(String stampedeIdString) {
    StampedeId stampedeId = new StampedeId();
    stampedeId.setId(stampedeIdString);
    return stampedeId;
  }

  private void expectMockLocalBuildExecutorReturnsWithCode(int code)
      throws IOException, InterruptedException {
    expect(
            mockLocalBuildExecutorInvoker.executeLocalBuild(
                anyBoolean(),
                anyObject(RemoteBuildRuleSynchronizer.class),
                anyObject(CountDownLatch.class),
                anyObject(AtomicReference.class)))
        .andReturn(code);
  }

  private void ensureTerminationOfBuild(Build buildMock, CountDownLatch buildPhaseLatch) {
    buildMock.terminateBuildWithFailure(anyObject(Throwable.class));
    EasyMock.expectLastCall()
        .andAnswer(
            () -> {
              // Build has terminated, so enable invoker to be called and return exit code.
              buildPhaseLatch.countDown();
              return null;
            });
  }

  private void assertLocalAndDistributedExitCodes(
      Future<Integer> result, int expectedLocalExitCode, int expectedDistExitCode)
      throws ExecutionException, InterruptedException {
    int localBuildExitCode = result.get();
    assertEquals(expectedLocalExitCode, localBuildExitCode);
    assertEquals(expectedDistExitCode, buildClient.getDistBuildExitCode());
  }

  private void replayAllMocks() {
    replay(mockLocalBuildExecutorInvoker, mockDistBuildControllerInvoker);
    replay(buildOneMock, buildTwoMock); // No calls expected on buildTwoMock
    replay(mockDistBuildService);
  }

  private void verifyAllMocks() {
    verify(mockLocalBuildExecutorInvoker, mockDistBuildControllerInvoker);
    verify(buildOneMock, buildTwoMock);
    verify(mockDistBuildService);
  }
}
