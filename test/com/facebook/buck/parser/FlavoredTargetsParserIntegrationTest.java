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

package com.facebook.buck.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.ZipArchive;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

public class FlavoredTargetsParserIntegrationTest {

  @Rule public TemporaryPaths tempFolder = new TemporaryPaths();

  @Test
  public void canBuildAnUnflavoredTarget() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "unflavored_build", tempFolder);
    workspace.setUp();

    Path output = workspace.buildAndReturnOutput("//:example");

    // The output of the rule should be a normal jar. Verify that.
    assertEquals("jar", MorePaths.getFileExtension(output));
    // Ensure the output name is not to be confused with a sources jar
    assertFalse(output.getFileName().toString().endsWith(Javac.SRC_JAR));
  }

  @Test
  public void canBuildAFlavoredTarget() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "unflavored_build", tempFolder);
    workspace.setUp();

    Path output = workspace.buildAndReturnOutput("//:example#src");

    // The output of the rule should be a src jar. Verify that.
    assertTrue(output.toString(), output.toString().endsWith(Javac.SRC_JAR));
  }

  @Test
  public void canBuildBothAFlavoredAndUnflavoredVersionOfTheSameTargetInTheSameBuild()
      throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "unflavored_build", tempFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckBuild("//:example", "//:example#src");
    result.assertSuccess();

    // Verify that both the src zip and the jar were created.
    result = workspace.runBuckCommand("targets", "--show-output", "//:example", "//:example#src");
    result.assertSuccess();
    String stdout = result.getStdout();
    List<String> paths = Splitter.on('\n').omitEmptyStrings().trimResults().splitToList(stdout);
    paths = Lists.reverse(paths);

    // There should be at least two paths output
    assertTrue(paths.toString(), paths.size() > 1);

    // The last two are the paths to the outputs
    Path first = workspace.getPath(paths.get(0).split("\\s+")[1]);
    Path second = workspace.getPath(paths.get(1).split("\\s+")[1]);

    assertTrue(Files.exists(first));
    assertTrue(Files.exists(second));
  }

  @Test
  public void canReferToFlavorsInBuildFiles() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this,
            "flavored_build", // NB: this is not the same as the other tests in this file!
            tempFolder);
    workspace.setUp();

    Path output = workspace.buildAndReturnOutput("//:example");

    // Take a look at the contents of 'output'. It should be a source jar.
    try (ZipArchive zipArchive = new ZipArchive(output, /* for writing? */ false)) {
      Set<String> fileNames = zipArchive.getFileNames();

      assertTrue(fileNames.toString(), fileNames.contains("B.java"));
    }
  }
}
