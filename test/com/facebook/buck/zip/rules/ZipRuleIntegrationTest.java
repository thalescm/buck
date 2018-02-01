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

package com.facebook.buck.zip.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

public class ZipRuleIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void shouldZipSources() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "zip-rule", tmp);
    workspace.setUp();

    Path zip = workspace.buildAndReturnOutput("//example:ziptastic");

    // Make sure we have the right files and attributes.
    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      ZipArchiveEntry cake = zipFile.getEntry("cake.txt");
      assertThat(cake, Matchers.notNullValue());
      assertFalse(cake.isUnixSymlink());
      assertFalse(cake.isDirectory());

      ZipArchiveEntry beans = zipFile.getEntry("beans/");
      assertThat(beans, Matchers.notNullValue());
      assertFalse(beans.isUnixSymlink());
      assertTrue(beans.isDirectory());

      ZipArchiveEntry cheesy = zipFile.getEntry("beans/cheesy.txt");
      assertThat(cheesy, Matchers.notNullValue());
      assertFalse(cheesy.isUnixSymlink());
      assertFalse(cheesy.isDirectory());
    }
  }

  @Test
  public void shouldUnpackContentsOfASrcJar() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "zip-rule", tmp);
    workspace.setUp();

    Path zip = workspace.buildAndReturnOutput("//example:unrolled");

    ZipInspector inspector = new ZipInspector(zip);
    inspector.assertFileExists("menu.txt");
  }

  @Test
  public void shouldSupportInputBasedRuleKey() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "zip-rule", tmp);
    workspace.setUp();
    // Warm the cache
    workspace.runBuckBuild("//example:inputbased");
    // Edit src in a non-output affecting fashion
    workspace.replaceFileContents("example/A.java", "ReplaceMe", "");
    // Re-build and expect input-based hit
    workspace.runBuckBuild("//example:inputbased");
    workspace.getBuildLog().assertTargetBuiltLocally("//example:lib");
    workspace.getBuildLog().assertTargetHadMatchingInputRuleKey("//example:inputbased");
  }

  @Test
  public void shouldFlattenZipsIfRequested() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "zip-flatten", tmp);
    workspace.setUp();
    // Warm the cache
    Path zip = workspace.buildAndReturnOutput("//example:flatten");

    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      ZipArchiveEntry cake = zipFile.getEntry("cake.txt");
      assertThat(cake, Matchers.notNullValue());

      ZipArchiveEntry beans = zipFile.getEntry("beans.txt");
      assertThat(beans, Matchers.notNullValue());
    }
  }

  @Test
  public void shouldNotMergeSourceJarsIfRequested() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "zip-merge", tmp);
    workspace.setUp();

    Path zip = workspace.buildAndReturnOutput("//example:no-merge");

    // Gather expected file names
    Path sourceJar = workspace.buildAndReturnOutput("//example:cake#src");
    Path actualJar = workspace.buildAndReturnOutput("//example:cake");

    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      ZipArchiveEntry item = zipFile.getEntry(sourceJar.getFileName().toString());
      assertThat(item, Matchers.notNullValue());

      item = zipFile.getEntry(actualJar.getFileName().toString());
      assertThat(item, Matchers.notNullValue());

      item = zipFile.getEntry("cake.txt");
      assertThat(item, Matchers.notNullValue());
    }
  }
}
