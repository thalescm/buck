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

package com.facebook.buck.cli;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.apple.AppleNativeIntegrationTestUtils;
import com.facebook.buck.apple.toolchain.ApplePlatform;
import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.config.FakeBuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.DefaultKnownBuildRuleTypesFactory;
import com.facebook.buck.rules.KnownBuildRuleTypesProvider;
import com.facebook.buck.rules.TestCellBuilder;
import com.facebook.buck.sandbox.TestSandboxExecutionStrategyFactory;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.FakeProcess;
import com.facebook.buck.util.FakeProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DaemonLifecycleManagerTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  private ProjectFilesystem filesystem;
  private DaemonLifecycleManager daemonLifecycleManager;
  private KnownBuildRuleTypesProvider knownBuildRuleTypesProvider;
  private BuckConfig buckConfig;
  private ExecutableFinder executableFinder;

  @Before
  public void setUp() throws InterruptedException {
    filesystem = TestProjectFilesystems.createProjectFilesystem(tmp.getRoot());
    buckConfig = FakeBuckConfig.builder().build();
    daemonLifecycleManager = new DaemonLifecycleManager();
    ProcessExecutor executor = new DefaultProcessExecutor(new TestConsole());
    knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                executor,
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));
    executableFinder = new ExecutableFinder();
  }

  @Test
  public void whenBuckConfigChangesParserInvalidated() throws IOException, InterruptedException {
    Object daemon =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder()
                .setBuckConfig(
                    FakeBuckConfig.builder()
                        .setSections(
                            ImmutableMap.of(
                                "somesection", ImmutableMap.of("somename", "somevalue")))
                        .build())
                .setFilesystem(filesystem)
                .build(),
            knownBuildRuleTypesProvider,
            executableFinder);

    assertEquals(
        "Daemon should not be replaced when config equal.",
        daemon,
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder()
                .setBuckConfig(
                    FakeBuckConfig.builder()
                        .setSections(
                            ImmutableMap.of(
                                "somesection", ImmutableMap.of("somename", "somevalue")))
                        .build())
                .setFilesystem(filesystem)
                .build(),
            knownBuildRuleTypesProvider,
            executableFinder));

    assertNotEquals(
        "Daemon should be replaced when config not equal.",
        daemon,
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder()
                .setBuckConfig(
                    FakeBuckConfig.builder()
                        .setSections(
                            ImmutableMap.of(
                                "somesection", ImmutableMap.of("somename", "someothervalue")))
                        .build())
                .setFilesystem(filesystem)
                .build(),
            knownBuildRuleTypesProvider,
            executableFinder));
  }

  @Test
  public void whenAndroidNdkVersionChangesParserInvalidated()
      throws IOException, InterruptedException {

    BuckConfig buckConfig1 =
        FakeBuckConfig.builder()
            .setSections(ImmutableMap.of("ndk", ImmutableMap.of("ndk_version", "something")))
            .build();

    BuckConfig buckConfig2 =
        FakeBuckConfig.builder()
            .setSections(ImmutableMap.of("ndk", ImmutableMap.of("ndk_version", "different")))
            .build();

    Object daemon =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig1).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);

    assertNotEquals(
        "Daemon should be replaced when not equal.",
        daemon,
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig2).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder));
  }

  @Test
  public void testAppleSdkChangesParserInvalidated() throws IOException, InterruptedException {
    assumeThat(Platform.detect(), is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    BuckConfig buckConfig = FakeBuckConfig.builder().build();

    FakeProcessExecutor fakeProcessExecutor = new FakeProcessExecutor();

    KnownBuildRuleTypesProvider knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                fakeProcessExecutor,
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));

    Object daemon1 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);

    Object daemon2 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    assertEquals("Apple SDK should still be not found", daemon1, daemon2);

    Path appleDeveloperDirectoryPath = tmp.newFolder("android-sdk").toAbsolutePath();

    BuckConfig buckConfigWithDeveloperDirectory =
        FakeBuckConfig.builder()
            .setSections(
                "[apple]",
                "xcode_developer_dir = " + appleDeveloperDirectoryPath.toAbsolutePath().toString())
            .build();

    Object daemon3 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder()
                .setBuckConfig(buckConfigWithDeveloperDirectory)
                .setFilesystem(filesystem)
                .build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    assertNotEquals("Apple SDK should be found", daemon2, daemon3);

    Object daemon4 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder()
                .setBuckConfig(buckConfigWithDeveloperDirectory)
                .setFilesystem(filesystem)
                .build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    assertEquals("Apple SDK should still be found", daemon3, daemon4);
  }

  @Test
  public void testAndroidSdkChangesParserInvalidated() throws IOException, InterruptedException {
    // Disable the test on Windows for now since it's failing to find python.
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    BuckConfig buckConfig = FakeBuckConfig.builder().build();
    ImmutableList.Builder<Map.Entry<ProcessExecutorParams, FakeProcess>> fakeProcessesBuilder =
        ImmutableList.builder();
    ProcessExecutorParams processExecutorParams =
        ProcessExecutorParams.builder()
            .setCommand(ImmutableList.of("xcode-select", "--print-path"))
            .build();
    // First KnownBuildRuleTypes resolution.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // Check SDK.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // Check SDK.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // KnownBuildRuleTypes resolution.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // Check SDK.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    FakeProcessExecutor fakeProcessExecutor = new FakeProcessExecutor(fakeProcessesBuilder.build());

    KnownBuildRuleTypesProvider knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                fakeProcessExecutor,
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));

    Object daemon1 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    Object daemon2 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    assertEquals("Android SDK should be the same initial location", daemon1, daemon2);

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();

    Cell cell = createCellWithAndroidSdk(androidSdkPath);

    Object daemon3 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);
    assertEquals("Daemon should not be re-created", daemon2, daemon3);
    Object daemon4 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);
    assertEquals("Android SDK should be the same other location", daemon3, daemon4);
  }

  @Test
  public void testAndroidSdkChangesParserInvalidatedWhenToolchainsPresent()
      throws IOException, InterruptedException {
    // Disable the test on Windows for now since it's failing to find python.
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    BuckConfig buckConfig = FakeBuckConfig.builder().build();
    ImmutableList.Builder<Map.Entry<ProcessExecutorParams, FakeProcess>> fakeProcessesBuilder =
        ImmutableList.builder();
    ProcessExecutorParams processExecutorParams =
        ProcessExecutorParams.builder()
            .setCommand(ImmutableList.of("xcode-select", "--print-path"))
            .build();
    // First KnownBuildRuleTypes resolution.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // Check SDK.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // Check SDK.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // KnownBuildRuleTypes resolution.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    // Check SDK.
    fakeProcessesBuilder.add(
        new SimpleImmutableEntry<>(processExecutorParams, new FakeProcess(0, "/dev/null", "")));
    FakeProcessExecutor fakeProcessExecutor = new FakeProcessExecutor(fakeProcessesBuilder.build());

    KnownBuildRuleTypesProvider knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                fakeProcessExecutor,
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));

    Object daemon1 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    Object daemon2 =
        daemonLifecycleManager.getDaemon(
            new TestCellBuilder().setBuckConfig(buckConfig).setFilesystem(filesystem).build(),
            knownBuildRuleTypesProvider,
            executableFinder);
    assertEquals("Android SDK should be the same initial location", daemon1, daemon2);

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();

    Cell cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider().getByName(AndroidSdkLocation.DEFAULT_NAME);

    Object daemon3 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);
    assertNotEquals("Android SDK should be the other location", daemon2, daemon3);
    Object daemon4 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);
    assertEquals("Android SDK should be the same other location", daemon3, daemon4);
  }

  private Cell createCellWithAndroidSdk(Path androidSdkPath)
      throws IOException, InterruptedException {
    return new TestCellBuilder()
        .setBuckConfig(buckConfig)
        .setFilesystem(filesystem)
        .addEnvironmentVariable("ANDROID_HOME", androidSdkPath.toString())
        .addEnvironmentVariable("ANDROID_SDK", androidSdkPath.toString())
        .build();
  }

  @Test
  public void testParserInvalidatedWhenToolchainFailsToCreateFirstTime()
      throws IOException, InterruptedException {
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();
    Files.deleteIfExists(androidSdkPath);

    Cell cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider()
        .getByNameIfPresent(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class);
    Daemon daemonWithBrokenAndroidSdk =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    tmp.newFolder("android-sdk");

    cell = createCellWithAndroidSdk(androidSdkPath);
    Daemon daemonWithWorkingAndroidSdk =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    assertNotEquals(daemonWithBrokenAndroidSdk, daemonWithWorkingAndroidSdk);
  }

  @Test
  public void testParserInvalidatedWhenToolchainFailsToCreateAfterFirstCreation()
      throws IOException, InterruptedException {
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();

    Cell cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider()
        .getByNameIfPresent(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class);
    Daemon daemonWithWorkingAndroidSdk =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    Files.deleteIfExists(androidSdkPath);

    cell = createCellWithAndroidSdk(androidSdkPath);
    Daemon daemonWithBrokenAndroidSdk =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    assertNotEquals(daemonWithWorkingAndroidSdk, daemonWithBrokenAndroidSdk);
  }

  @Test
  public void testParserNotInvalidatedWhenToolchainFailsWithTheSameProblem()
      throws IOException, InterruptedException {
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();
    Files.deleteIfExists(androidSdkPath);

    Cell cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider()
        .getByNameIfPresent(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class);
    Daemon daemonWithBrokenAndroidSdk1 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider()
        .getByNameIfPresent(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class);
    Daemon daemonWithBrokenAndroidSdk2 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    assertEquals(daemonWithBrokenAndroidSdk1, daemonWithBrokenAndroidSdk2);
  }

  @Test
  public void testParserNotInvalidatedWhenToolchainFailsWithTheSameProblemButNotInstantiated()
      throws IOException, InterruptedException {
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();
    Files.deleteIfExists(androidSdkPath);

    Cell cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider()
        .getByNameIfPresent(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class);
    Daemon daemonWithBrokenAndroidSdk1 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    cell = createCellWithAndroidSdk(androidSdkPath);
    Daemon daemonWithBrokenAndroidSdk2 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    assertEquals(daemonWithBrokenAndroidSdk1, daemonWithBrokenAndroidSdk2);
  }

  @Test
  public void testParserInvalidatedWhenToolchainFailsWithDifferentProblem()
      throws IOException, InterruptedException {
    assumeThat(Platform.detect(), not(Platform.WINDOWS));

    Path androidSdkPath = tmp.newFolder("android-sdk").toAbsolutePath();
    Files.deleteIfExists(androidSdkPath);

    Cell cell = createCellWithAndroidSdk(androidSdkPath);
    cell.getToolchainProvider()
        .getByNameIfPresent(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class);
    Object daemonWithBrokenAndroidSdk1 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    cell = createCellWithAndroidSdk(androidSdkPath.resolve("some-other-dir"));
    Object daemonWithBrokenAndroidSdk2 =
        daemonLifecycleManager.getDaemon(cell, knownBuildRuleTypesProvider, executableFinder);

    assertNotEquals(daemonWithBrokenAndroidSdk1, daemonWithBrokenAndroidSdk2);
  }
}
