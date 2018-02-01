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

package com.facebook.buck.cli;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.ExitCode;
import com.google.common.base.Joiner;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Integration test for the {@code buck build} command with no arguments. */
public class BuildWithNoTargetSpecifiedIntegrationTest {

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Test
  public void testBuckBuildWithoutTarget() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "project_with_no_alias", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("build");
    result.assertExitCode("buck build should exit with an error.", ExitCode.COMMANDLINE_ERROR);

    assertThat(
        "`buck build` should display an error message if no targets are provided.",
        result.getStderr(),
        containsString("Must specify at least one build target.\n"));
  }

  @Test
  public void testBuckBuildWithoutTargetWithSingleAliasConfigured() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "project_with_one_alias", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("build");
    result.assertExitCode("buck build should exit with an error.", ExitCode.COMMANDLINE_ERROR);

    assertThat(
        "`buck build` should suggest the alias found in .buckconfig as a target.",
        result.getStderr(),
        containsString(
            Joiner.on('\n')
                    .join(
                        "Must specify at least one build target.",
                        "Try building one of the following targets:",
                        "myapp")
                + '\n'));
  }

  /**
   * Ensure that if there are multiple aliases (but less than ten) they are all displayed as targets
   * in the order specified in .buckconfig.
   */
  @Test
  public void testBuckBuildWithoutTargetWithUnderTenAliasesConfigured() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "project_with_five_aliases", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("build");
    result.assertExitCode("buck build should exit with an error.", ExitCode.COMMANDLINE_ERROR);

    assertThat(
        Joiner.on(' ')
            .join(
                "`buck build` should suggest the five aliases found in .buckconfig",
                "(in the order in which they are listed in) as targets."),
        result.getStderr(),
        containsString(
            Joiner.on('\n')
                    .join(
                        "Must specify at least one build target.",
                        "Try building one of the following targets:",
                        "myapp my_app mi_app mon_app mein_app")
                + '\n'));
  }

  /**
   * Ensure that if there are more than ten aliases only the first ten (based on the order specified
   * in .buckconfig) are displayed as targets.
   */
  @Test
  public void testBuckBuildWithoutTargetWithOverTenAliasesConfigured() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "project_with_fifteen_aliases", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("build");
    result.assertExitCode("buck build should exit with an error.", ExitCode.COMMANDLINE_ERROR);

    assertThat(
        Joiner.on(' ')
            .join(
                "`buck build` should suggest the first ten aliases found in .buckconfig",
                "(in the order in which they are listed in) as targets."),
        result.getStderr(),
        containsString(
            Joiner.on('\n')
                    .join(
                        "Must specify at least one build target.",
                        "Try building one of the following targets:",
                        "myapp my_app mi_app mon_app mein_app alias0 alias1 alias2 alias3 alias4")
                + '\n'));
  }
}
