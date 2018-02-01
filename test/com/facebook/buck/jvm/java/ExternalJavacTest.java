/*
 * Copyright 2012-present Facebook, Inc.
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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.VersionedTool;
import com.facebook.buck.rules.keys.AlterRuleKeys;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.FakeProcess;
import com.facebook.buck.util.FakeProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.types.Either;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import org.easymock.Capture;
import org.easymock.EasyMockSupport;
import org.junit.Rule;
import org.junit.Test;

public class ExternalJavacTest extends EasyMockSupport {
  private static final Path PATH_TO_SRCS_LIST = Paths.get("srcs_list");
  public static final ImmutableSortedSet<Path> SOURCE_PATHS =
      ImmutableSortedSet.of(Paths.get("foobar.java"));

  @Rule public TemporaryPaths root = new TemporaryPaths();

  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths();

  @Test
  public void testJavacCommand() {
    ExternalJavac firstOrder = createTestStep();
    ExternalJavac warn = createTestStep();
    ExternalJavac transitive = createTestStep();

    assertEquals(
        "fakeJavac -source 6 -target 6 -g -d . -classpath foo.jar @" + PATH_TO_SRCS_LIST,
        firstOrder.getDescription(
            getArgs().add("foo.jar").build(), SOURCE_PATHS, PATH_TO_SRCS_LIST));
    assertEquals(
        "fakeJavac -source 6 -target 6 -g -d . -classpath foo.jar @" + PATH_TO_SRCS_LIST,
        warn.getDescription(getArgs().add("foo.jar").build(), SOURCE_PATHS, PATH_TO_SRCS_LIST));
    assertEquals(
        "fakeJavac -source 6 -target 6 -g -d . -classpath bar.jar"
            + File.pathSeparator
            + "foo.jar @"
            + PATH_TO_SRCS_LIST,
        transitive.getDescription(
            getArgs().add("bar.jar" + File.pathSeparator + "foo.jar").build(),
            SOURCE_PATHS,
            PATH_TO_SRCS_LIST));
  }

  @Test
  public void externalJavacWillHashTheExternalIfNoVersionInformationIsReturned()
      throws IOException {
    // TODO(cjhopman): This test name implies we should be hashing the external file not just
    // adding its path.
    Path javac = Files.createTempFile("fake", "javac");
    javac.toFile().deleteOnExit();
    ProcessExecutorParams javacExe =
        ProcessExecutorParams.builder()
            .addCommand(javac.toAbsolutePath().toString(), "-version")
            .build();
    FakeProcess javacProc = new FakeProcess(0, "", "");
    final FakeProcessExecutor executor =
        new FakeProcessExecutor(ImmutableMap.of(javacExe, javacProc));
    ExternalJavac compiler =
        new ExternalJavac(Either.ofLeft(javac)) {
          @Override
          ProcessExecutor createProcessExecutor() {
            return executor;
          }
        };
    RuleKeyObjectSink sink = createMock(RuleKeyObjectSink.class);
    Capture<Supplier<Tool>> identifier = new Capture<>();
    expect(sink.setReflectively(eq(".class"), anyObject())).andReturn(sink);
    expect(sink.setReflectively(eq("javac"), capture(identifier))).andReturn(sink);
    replay(sink);
    AlterRuleKeys.amendKey(sink, compiler);
    verify(sink);
    Tool tool = identifier.getValue().get();

    assertTrue(tool instanceof VersionedTool);
    assertEquals(javac.toString(), ((VersionedTool) tool).getVersion());
  }

  @Test
  public void externalJavacWillHashTheJavacVersionIfPresent() throws IOException {
    Path javac = Files.createTempFile("fake", "javac");
    javac.toFile().deleteOnExit();
    String reportedJavacVersion = "mozzarella";

    JavacVersion javacVersion = JavacVersion.of(reportedJavacVersion);
    ProcessExecutorParams javacExe =
        ProcessExecutorParams.builder()
            .addCommand(javac.toAbsolutePath().toString(), "-version")
            .build();
    FakeProcess javacProc = new FakeProcess(0, "", reportedJavacVersion);
    final FakeProcessExecutor executor =
        new FakeProcessExecutor(ImmutableMap.of(javacExe, javacProc));
    ExternalJavac compiler =
        new ExternalJavac(Either.ofLeft(javac)) {
          @Override
          ProcessExecutor createProcessExecutor() {
            return executor;
          }
        };

    RuleKeyObjectSink sink = createMock(RuleKeyObjectSink.class);
    Capture<Supplier<Tool>> identifier = new Capture<>();
    expect(sink.setReflectively(eq(".class"), anyObject())).andReturn(sink);
    expect(sink.setReflectively(eq("javac"), capture(identifier))).andReturn(sink);
    replay(sink);
    AlterRuleKeys.amendKey(sink, compiler);
    verify(sink);
    Tool tool = identifier.getValue().get();

    assertTrue(tool instanceof VersionedTool);
    assertEquals(javacVersion.toString(), ((VersionedTool) tool).getVersion());
  }

  private ImmutableList.Builder<String> getArgs() {
    return ImmutableList.<String>builder()
        .add("-source", "6", "-target", "6", "-g", "-d", ".", "-classpath");
  }

  private ExternalJavac createTestStep() {
    Path fakeJavac = Paths.get("fakeJavac");
    return new ExternalJavac(Either.ofLeft(fakeJavac));
  }
}
