/*
 * Copyright 2013-present Facebook, Inc.
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

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class CompileSrcZipIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void testJavaLibraryRuleAcceptsZipFileOfJavaSourceCodeAsInput() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "src_zip", tmp);
    workspace.setUp();

    ProcessResult buildResult = workspace.runBuckCommand("build", "//:lib", "-v", "2");
    buildResult.assertSuccess("Successful build should exit with 0.");
  }
}
