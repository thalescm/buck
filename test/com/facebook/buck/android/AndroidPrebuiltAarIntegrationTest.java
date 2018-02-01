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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.jvm.java.testutil.AbiCompilationModeTest;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AndroidPrebuiltAarIntegrationTest extends AbiCompilationModeTest {

  private ProjectWorkspace workspace;

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Before
  public void setUp() throws InterruptedException, IOException {
    AssumeAndroidPlatform.assumeSdkIsAvailable();
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_prebuilt_aar", tmp, true);
    workspace.setUp();
    setWorkspaceCompilationMode(workspace);
  }

  @Test
  public void testBuildAndroidPrebuiltAar() throws InterruptedException, IOException {
    String target = "//:app";
    workspace.runBuckBuild(target).assertSuccess();
    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargets.getGenPath(
                    TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath()),
                    BuildTargetFactory.newInstance(target),
                    "%s.apk")));
    zipInspector.assertFileExists("AndroidManifest.xml");
    zipInspector.assertFileExists("resources.arsc");
    zipInspector.assertFileExists("classes.dex");
    zipInspector.assertFileExists("lib/x86/liba.so");
  }

  @Test
  public void testPrebuiltJarInDepsIsExported() throws IOException {
    workspace.runBuckBuild("//prebuilt_jar-dep:lib").assertSuccess();
  }

  @Test
  public void testAndroidPrebuiltAarInDepsIsExported() throws IOException {
    workspace.runBuckBuild("//android_prebuilt_aar-dep:lib").assertSuccess();
  }

  @Test
  public void testPrebuiltRDotTxtContainsTransitiveDependencies()
      throws InterruptedException, IOException {
    String target = "//third-party/design-library:design-library";
    workspace.runBuckBuild(target).assertSuccess();

    String appCompatResource = "TextAppearance_AppCompat_Body2";

    String rDotTxt =
        workspace.getFileContents(
            BuildTargets.getScratchPath(
                TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath()),
                BuildTargetFactory.newInstance(target)
                    .withFlavors(AndroidPrebuiltAarDescription.AAR_UNZIP_FLAVOR),
                "__unpack_%s__/R.txt"));
    assertThat(
        "R.txt contains transitive dependencies", rDotTxt, containsString(appCompatResource));
  }

  @Test
  public void testExtraDepsDontResultInWarning() throws IOException {
    ProcessResult result = workspace.runBuckBuild("//:app-extra-res-entry").assertSuccess();

    String buildOutput = result.getStderr();
    assertThat("No warnings are shown", buildOutput, not(containsString("Cannot find resource")));
  }

  @Test
  public void testNoClassesDotJar() throws IOException {
    workspace.runBuckBuild("//:app-no-classes-dot-jar").assertSuccess();
  }
}
