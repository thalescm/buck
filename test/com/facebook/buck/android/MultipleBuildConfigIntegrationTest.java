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

package com.facebook.buck.android;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;

public class MultipleBuildConfigIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  /** Regression test for https://github.com/facebook/buck/issues/187. */
  @Test
  public void testAndroidBinarySupportsMultipleBuildConfigs()
      throws InterruptedException, IOException {
    AssumeAndroidPlatform.assumeSdkIsAvailable();
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmp);
    workspace.setUp();

    Path outputFile = workspace.buildAndReturnOutput("//java/com/buildconfigs:extract-classes-dex");
    String smali = new String(Files.readAllBytes(outputFile), UTF_8);

    assertThat(
        smali, containsString(Paths.get("com/example/config1/BuildConfig.smali").toString()));
    assertThat(
        smali, containsString(Paths.get("com/example/config2/BuildConfig.smali").toString()));
  }
}
