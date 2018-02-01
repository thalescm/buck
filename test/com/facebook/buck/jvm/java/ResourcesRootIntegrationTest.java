/*
 * Copyright 2014-present Facebook, Inc.
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
package com.facebook.buck.jvm.java;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.ProcessExecutor;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;

public class ResourcesRootIntegrationTest {

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Test
  public void testResourcePathRelativeToResourcesRoot() throws IOException, InterruptedException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "resources_root", temporaryFolder);
    workspace.setUp();

    Path supWorldJar = workspace.buildAndReturnOutput("//java/com/example:SupWorld");
    ZipInspector inspector = new ZipInspector(supWorldJar);
    inspector.assertFileExists("com/another/supworld.txt");

    ProcessExecutor.Result result = workspace.runJar(supWorldJar);
    assertEquals(
        "SupWorld should print the resource file's contents.",
        "nuthin much\n",
        result.getStdout().get());
    assertEquals("", result.getStderr().get());
  }
}
