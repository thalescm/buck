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

package com.facebook.buck.python;

import com.facebook.buck.config.FakeBuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.python.toolchain.impl.PythonPlatformsProviderFactoryUtils;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.Verbosity;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PrebuiltPythonLibraryIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  public ProjectWorkspace workspace;

  @Before
  public void setUp() throws IOException, InterruptedException {
    workspace = TestDataHelper.createProjectWorkspaceForScenario(this, "prebuilt_package", tmp);
    workspace.setUp();

    // EGGs are versioned to the version of Python they were built it, but the EGG for this test
    // doesn't actually matter.
    String version =
        PythonPlatformsProviderFactoryUtils.getPythonEnvironment(
                FakeBuckConfig.builder().build(),
                new DefaultProcessExecutor(new TestConsole(Verbosity.SILENT)),
                new ExecutableFinder())
            .getPythonVersion()
            .getVersionString();
    if (!version.startsWith("2.6")) {
      workspace.move(
          "dist/package-0.1-py2.6.egg", "dist/package-0.1-py" + version.substring(0, 3) + ".egg");
    }
  }

  @Test
  public void testRunPexWithEggDependency() throws IOException {
    ProcessResult eggResults = workspace.runBuckCommand("run", "//:main_egg");
    eggResults.assertSuccess();

    ProcessResult whlResults = workspace.runBuckCommand("run", "//:main_whl");
    whlResults.assertSuccess();
  }
}
