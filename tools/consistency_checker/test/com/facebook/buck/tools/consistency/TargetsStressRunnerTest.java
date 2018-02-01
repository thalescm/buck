/*
 * Copyright 2017-present Facebook, Inc.
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

package com.facebook.buck.tools.consistency;

import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.tools.consistency.BuckStressRunner.StressorException;
import com.facebook.buck.tools.consistency.DifferState.MaxDifferencesException;
import com.facebook.buck.tools.consistency.RuleKeyLogFileReader.ParseException;
import com.facebook.buck.tools.consistency.TargetsStressRunner.TargetsStressRunException;
import com.google.common.collect.ImmutableList;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TargetsStressRunnerTest {

  @Rule public TemporaryPaths temporaryPaths = new TemporaryPaths();

  @Rule public ExpectedException expectedException = ExpectedException.none();

  Callable<TargetsDiffer> differFactory;
  TestPrintStream stream = TestPrintStream.create();
  private TestBinWriter binWriter;
  private Path tempBinPath;

  @Before
  public void setUp() throws IOException {
    tempBinPath = temporaryPaths.newFile("buck_bin.py");
    binWriter = new TestBinWriter(tempBinPath);
    differFactory =
        () -> {
          DifferState differState = new DifferState(DifferState.INFINITE_DIFFERENCES);
          DiffPrinter diffPrinter = new DiffPrinter(stream, false);
          TargetsDiffer differ = new TargetsDiffer(diffPrinter, differState);
          return differ;
        };
  }

  @Test
  public void returnsErrorIfWritingTargetsFails() throws IOException, StressorException {
    expectedException.expect(StressorException.class);
    expectedException.expectMessage("Got a non-zero return code: 1");
    binWriter.writeArgEchoer(1);

    List<String> expectedCommand =
        ImmutableList.of(
            System.getProperty("user.dir"),
            tempBinPath.toAbsolutePath().toString(),
            "query",
            "deps(%s)",
            "@",
            "Random hashes configured",
            "Reading arguments from @",
            "//:target1",
            "//:target2");

    Path targetsFile = temporaryPaths.newFile("targets_list");
    try {
      TargetsStressRunner runner =
          new TargetsStressRunner(
              differFactory,
              Optional.of("python"),
              tempBinPath.toAbsolutePath().toString(),
              ImmutableList.of(),
              ImmutableList.of("//:target1", "//:target2"));
      runner.writeTargetsListToFile(targetsFile);
    } catch (Exception e) {
      assertAllStartWithPrefix(Files.readAllLines(targetsFile), expectedCommand);
      throw e;
    }
  }

  @Test
  public void returnsErrorIfFileCouldNotBeWritten() throws IOException, StressorException {
    expectedException.expect(StressorException.class);
    expectedException.expectMessage("Could not write query results");
    binWriter.writeArgEchoer(0);

    Path targetsFile = temporaryPaths.getRoot().resolve("nonexistent").resolve("path");
    binWriter.writeLineEchoer(new String[] {"//:target1", "//:target2", "//:target3"}, 0);
    TargetsStressRunner runner =
        new TargetsStressRunner(
            differFactory,
            Optional.of("python"),
            tempBinPath.toAbsolutePath().toString(),
            ImmutableList.of(),
            ImmutableList.of("//:target1", "//:target2"));
    runner.writeTargetsListToFile(targetsFile);
  }

  @Test
  public void writesTargetListsToFile() throws IOException, StressorException {
    Path targetsFile = temporaryPaths.newFile("targets_list");
    binWriter.writeLineEchoer(new String[] {"//:target1", "//:target2", "//:target3"}, 0);
    TargetsStressRunner runner =
        new TargetsStressRunner(
            differFactory,
            Optional.of("python"),
            tempBinPath.toAbsolutePath().toString(),
            ImmutableList.of(),
            ImmutableList.of("//:target1", "//:target2"));

    runner.writeTargetsListToFile(targetsFile);

    List<String> lines = Files.readAllLines(targetsFile);
    Assert.assertEquals(3, lines.size());
    Assert.assertEquals("//:target1", lines.get(0));
    Assert.assertEquals("//:target2", lines.get(1));
    Assert.assertEquals("//:target3", lines.get(2));
  }

  @Test
  public void getsCorrectBuckRunners() throws IOException, InterruptedException {
    Path targetsFile = temporaryPaths.newFile("targets_list");
    TestPrintStream testStream1 = TestPrintStream.create();
    TestPrintStream testStream2 = TestPrintStream.create();
    binWriter.writeArgEchoer(0);
    TargetsStressRunner runner =
        new TargetsStressRunner(
            differFactory,
            Optional.of("python"),
            tempBinPath.toAbsolutePath().toString(),
            ImmutableList.of("-c", "config=value"),
            ImmutableList.of("//:target1", "//:target2"));

    List<String> expectedCommand =
        ImmutableList.of(
            System.getProperty("user.dir"),
            tempBinPath.toAbsolutePath().toString(),
            "targets",
            "-c",
            "config=value",
            "--show-target-hash",
            String.format("@%s", targetsFile.toAbsolutePath().toString()),
            "Random hashes configured",
            "Reading arguments from @");

    List<BuckRunner> runners = runner.getBuckRunners(2, targetsFile, Optional.empty());

    Assert.assertEquals(2, runners.size());
    runners.get(0).run(testStream1);
    runners.get(1).run(testStream2);
    ImmutableList<String> outputLines1 = ImmutableList.copyOf(testStream1.getOutputLines());
    ImmutableList<String> outputLines2 = ImmutableList.copyOf(testStream2.getOutputLines());

    assertAllStartWithPrefix(outputLines1, expectedCommand);
    assertAllStartWithPrefix(outputLines2, expectedCommand);
  }

  private void assertAllStartWithPrefix(List<String> lines, List<String> expectedPrefixes) {
    Assert.assertEquals(expectedPrefixes.size(), lines.size());
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String expected = expectedPrefixes.get(i);
      Assert.assertTrue(
          String.format("Line %s must start with %s", line, expected), line.startsWith(expected));
    }
  }

  @Test
  public void throwsExceptionIfDifferenceIsFound()
      throws IOException, ParseException, TargetsStressRunException, MaxDifferencesException {

    Path file1 = temporaryPaths.newFile("1");
    Path file2 = temporaryPaths.newFile("2");
    Path file3 = temporaryPaths.newFile("3");

    expectedException.expect(TargetsStressRunException.class);
    expectedException.expectMessage(
        String.format(
            "Found differences between %s and %s", file1.toAbsolutePath(), file3.toAbsolutePath()));

    try (BufferedWriter output1 = Files.newBufferedWriter(file1);
        BufferedWriter output2 = Files.newBufferedWriter(file2);
        BufferedWriter output3 = Files.newBufferedWriter(file3)) {
      output1.write("//:target1 32bbfed4bbbb971d9499b016df1d4358cc4ad4ba");
      output2.write("//:target1 32bbfed4bbbb971d9499b016df1d4358cc4ad4ba");
      output3.write("//:target1 32bbfed4bbbb971d9499b016df1d4358cc4ad4ba");
      output1.newLine();
      output2.newLine();
      output3.newLine();

      output1.write("//:target2 81643a1508128186137c6b03d13b6352d6c5dfaf");
      output2.write("//:target2 81643a1508128186137c6b03d13b6352d6c5dfaf");
      output3.write("//:target2 a59619898666a2d5b3e1669a4293301b799763c1");
      output1.newLine();
      output2.newLine();
      output3.newLine();
    }

    TargetsStressRunner runner =
        new TargetsStressRunner(
            differFactory,
            Optional.of("python"),
            tempBinPath.toAbsolutePath().toString(),
            ImmutableList.of("-c", "config=value"),
            ImmutableList.of("//:target1", "//:target2"));
    runner.verifyNoChanges(file1, ImmutableList.of(file2, file3));
  }

  @Test
  public void returnsNormallyIfNoChanges()
      throws IOException, ParseException, TargetsStressRunException, MaxDifferencesException {

    Path file1 = temporaryPaths.newFile("1");
    Path file2 = temporaryPaths.newFile("2");
    Path file3 = temporaryPaths.newFile("3");

    try (BufferedWriter output1 = Files.newBufferedWriter(file1);
        BufferedWriter output2 = Files.newBufferedWriter(file2);
        BufferedWriter output3 = Files.newBufferedWriter(file3)) {
      output1.write("//:target1 32bbfed4bbbb971d9499b016df1d4358cc4ad4ba");
      output2.write("//:target1 32bbfed4bbbb971d9499b016df1d4358cc4ad4ba");
      output3.write("//:target1 32bbfed4bbbb971d9499b016df1d4358cc4ad4ba");
      output1.newLine();
      output2.newLine();
      output3.newLine();

      output1.write("//:target2 81643a1508128186137c6b03d13b6352d6c5dfaf");
      output2.write("//:target2 81643a1508128186137c6b03d13b6352d6c5dfaf");
      output3.write("//:target2 81643a1508128186137c6b03d13b6352d6c5dfaf");
      output1.newLine();
      output2.newLine();
      output3.newLine();
    }

    TargetsStressRunner runner =
        new TargetsStressRunner(
            differFactory,
            Optional.of("python"),
            tempBinPath.toAbsolutePath().toString(),
            ImmutableList.of("-c", "config=value"),
            ImmutableList.of("//:target1", "//:target2"));
    runner.verifyNoChanges(file1, ImmutableList.of(file2, file3));
  }
}
