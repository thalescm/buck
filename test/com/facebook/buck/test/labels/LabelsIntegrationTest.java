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

package com.facebook.buck.test.labels;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LabelsIntegrationTest {
  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  private ProjectWorkspace workspace;

  @Before
  public void setUpWorkspace() throws IOException {
    workspace = TestDataHelper.createProjectWorkspaceForScenario(this, "labels", temporaryFolder);
    workspace.setUp();
  }

  @Test
  public void shouldFailWithDashDashAll() throws IOException {
    assertTestsFail("test", "--all");
  }

  @Test
  public void shouldFailWithExplicitTargetsThatReferToFailingTests() throws IOException {
    assertTestsFail("test", "//test:geometry", "//test:orientation");
  }

  @Test
  public void shouldPassWithDashDashAllWhenExcludingUnscientificTests() throws IOException {
    assertTestsPass("test", "--all", "--exclude", "unscientific");
  }

  @Test
  public void shouldPassWithDashDashAllWhenIncludingOnlyScientificTests() throws IOException {
    assertTestsPass("test", "--all", "--include", "scientific");
  }

  /**
   * This will fail because, as stated in TestCommand.java: "We always want to run the rules that
   * are given on the command line. Always. Unless we don't want to."
   */
  @Test
  public void shouldFailBecauseWeExplicitlyAskedForAFailingTestToRunEvenThoughWeTriedToExcludeIt()
      throws IOException {
    assertTestsFail("test", "//test:geometry", "//test:orientation", "--exclude", "unscientific");
  }

  /**
   * This will succeed, as stated in TestCommand.java: "We always want to run the rules that are
   * given on the command line. Always. Unless we don't want to."
   */
  @Test
  public void shouldPassWhenFailingTestIncludedAndExcludedWithAlwaysExcludeFlag()
      throws IOException {
    assertTestsPass(
        "test",
        "//test:geometry",
        "//test:orientation",
        "--exclude",
        "unscientific",
        "--always_exclude");
  }

  @Test
  public void shouldIgnoreFailingTestWithTheCorrectLabelConjunction() throws IOException {
    // Both the passing test (PhotonsTest) and the failing test (EarthIsFlatTest) are labelled
    // testy, so asking for all tests that match "testy OR lighty" will fail.
    //
    // NB: A bug in the way args are parsed means that even though "testy light" is a single arg
    // here, it is split into multiple labels in by TestCommand.
    assertTestsFail("test", "--all", "--include", "testy", "lighty");

    // ...but "testy AND lighty" only matches the passing test.
    assertTestsPass("test", "--all", "--include", "testy+lighty");
  }

  private void assertTestsFail(String... args) throws IOException {
    ProcessResult result = workspace.runBuckCommand(args);
    result.assertTestFailure();
    assertThat(result.getStderr(), containsString("Earth should be flat!"));
    assertThat(result.getStderr(), containsString("TESTS FAILED: 1 FAILURE"));
  }

  private void assertTestsPass(String... args) throws IOException {
    ProcessResult result = workspace.runBuckCommand(args);
    result.assertSuccess();
    assertThat(result.getStderr(), containsString("TESTS PASSED"));
  }
}
