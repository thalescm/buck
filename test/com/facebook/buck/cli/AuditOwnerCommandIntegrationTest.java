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
package com.facebook.buck.cli;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class AuditOwnerCommandIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void testOwnerOneFile() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "audit_owner", tmp);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("audit", "owner", "example/1.txt");
    result.assertSuccess();
    assertEquals(workspace.getFileContents("stdout-one"), result.getStdout());
  }

  @Test
  public void testTwoFilesJSON() throws IOException {
    boolean isPlatformWindows = Platform.detect() == Platform.WINDOWS;
    String expectedJson = isPlatformWindows ? "stdout-one-two-windows.json" : "stdout-one-two.json";
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "audit_owner", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "audit",
            "owner",
            "--json",
            isPlatformWindows ? "example\\1.txt" : "example/1.txt",
            isPlatformWindows ? "example\\lib\\2.txt" : "example/lib/2.txt");
    result.assertSuccess();
    assertEquals(workspace.getFileContents(expectedJson), result.getStdout());
  }
}
