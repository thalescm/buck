/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.d;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.BuckBuildLog;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.ProcessExecutor;
import org.junit.Rule;
import org.junit.Test;

public class DLibraryIntegrationTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void compileAndRun() throws Exception {
    Assumptions.assumeDCompilerUsable();

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "library", tmp);
    workspace.setUp();

    workspace.runBuckBuild("-v", "10", "//:greet").assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//:greet");
    buildLog.assertTargetBuiltLocally("//:greeting");
    workspace.resetBuildLogFile();

    ProcessExecutor.Result result =
        workspace.runCommand(
            workspace
                .resolve(
                    BuildTargets.getGenPath(
                        new FakeProjectFilesystem(),
                        BuildTargetFactory.newInstance("//:greet")
                            .withFlavors(DBinaryDescription.BINARY_FLAVOR),
                        "%s/greet"))
                .toString());
    assertEquals(0, result.getExitCode());
    assertEquals("Hello, world!\n", result.getStdout().get());
    assertEquals("", result.getStderr().get());
  }
}
