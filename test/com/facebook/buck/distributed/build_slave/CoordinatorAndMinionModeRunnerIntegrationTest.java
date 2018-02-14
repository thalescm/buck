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

package com.facebook.buck.distributed.build_slave;

import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.build_slave.MinionModeRunnerIntegrationTest.FakeBuildExecutorImpl;
import com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory;
import com.facebook.buck.distributed.thrift.BuildSlaveRunId;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.event.listener.NoOpCoordinatorBuildRuleEventsPublisher;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.util.BuckConstant;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CoordinatorAndMinionModeRunnerIntegrationTest {

  private static final StampedeId STAMPEDE_ID = ThriftCoordinatorServerIntegrationTest.STAMPEDE_ID;
  private static final int CONNECTION_TIMEOUT_MILLIS = 1000;
  private static final int MAX_PARALLEL_WORK_UNITS = 10;
  private static final long POLL_LOOP_INTERVAL_MILLIS = 8;
  private static final DistBuildService MOCK_SERVICE =
      EasyMock.createNiceMock(DistBuildService.class);

  private HeartbeatService heartbeatService;

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  @Before
  public void setUp() {
    heartbeatService = MinionModeRunnerIntegrationTest.createFakeHeartbeatService();
  }

  @Test
  public void testDiamondGraphRun()
      throws IOException, NoSuchBuildTargetException, InterruptedException {

    Path logDirectoryPath = tempDir.getRoot().toPath();

    ThriftCoordinatorServer.EventListener eventListener =
        EasyMock.createNiceMock(ThriftCoordinatorServer.EventListener.class);
    SettableFuture<BuildTargetsQueue> queueFuture = SettableFuture.create();
    queueFuture.set(BuildTargetsQueueTest.createDiamondDependencyQueue());

    CoordinatorModeRunner coordinator =
        new CoordinatorModeRunner(
            queueFuture,
            STAMPEDE_ID,
            eventListener,
            logDirectoryPath,
            new NoOpCoordinatorBuildRuleEventsPublisher(),
            MOCK_SERVICE,
            Optional.of(new BuildId("10-20")),
            Optional.empty(),
            EasyMock.createNiceMock(MinionHealthTracker.class));
    FakeBuildExecutorImpl localBuilder = new FakeBuildExecutorImpl();
    MinionModeRunner minion =
        new MinionModeRunner(
            "localhost",
            OptionalInt.empty(),
            Futures.immediateFuture(localBuilder),
            STAMPEDE_ID,
            new BuildSlaveRunId().setId("sl7"),
            MAX_PARALLEL_WORK_UNITS,
            EasyMock.createNiceMock(MinionModeRunner.BuildCompletionChecker.class),
            POLL_LOOP_INTERVAL_MILLIS,
            new NoOpMinionBuildProgressTracker(),
            CONNECTION_TIMEOUT_MILLIS);
    CoordinatorAndMinionModeRunner jointRunner =
        new CoordinatorAndMinionModeRunner(coordinator, minion);
    int exitCode = jointRunner.runAndReturnExitCode(heartbeatService);
    Assert.assertEquals(0, exitCode);
    Assert.assertEquals(4, localBuilder.getBuildTargets().size());
    Assert.assertEquals(
        CustomBuildRuleResolverFactory.ROOT_TARGET, localBuilder.getBuildTargets().get(3));

    Path buildTracePath = logDirectoryPath.resolve(BuckConstant.DIST_BUILD_TRACE_FILE_NAME);
    Assert.assertTrue(buildTracePath.toFile().exists());
  }
}
