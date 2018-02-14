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

import com.facebook.buck.artifact_cache.ArtifactCache;
import com.facebook.buck.artifact_cache.NoopArtifactCache;
import com.facebook.buck.artifact_cache.SingletonArtifactCacheFactory;
import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.config.FakeBuckConfig;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.listener.BroadcastEventListener;
import com.facebook.buck.httpserver.WebServer;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.filesystem.impl.DefaultProjectFilesystemFactory;
import com.facebook.buck.jvm.core.JavaPackageFinder;
import com.facebook.buck.jvm.java.FakeJavaPackageFinder;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.rules.ActionGraphCache;
import com.facebook.buck.rules.BuildInfoStoreManager;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.DefaultKnownBuildRuleTypesFactory;
import com.facebook.buck.rules.KnownBuildRuleTypesProvider;
import com.facebook.buck.rules.TestCellBuilder;
import com.facebook.buck.rules.coercer.ConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.rules.keys.config.TestRuleKeyConfigurationFactory;
import com.facebook.buck.sandbox.TestSandboxExecutionStrategyFactory;
import com.facebook.buck.step.ExecutorPool;
import com.facebook.buck.testutil.FakeExecutor;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.cache.NoOpCacheStatsTracker;
import com.facebook.buck.util.cache.impl.StackedFileHashCache;
import com.facebook.buck.util.environment.BuildEnvironmentDescription;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.timing.DefaultClock;
import com.facebook.buck.util.versioncontrol.NoOpCmdLineInterface;
import com.facebook.buck.util.versioncontrol.VersionControlStatsGenerator;
import com.facebook.buck.versions.InstrumentedVersionedTargetGraphCache;
import com.facebook.buck.versions.VersionedTargetGraphCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

public class CommandRunnerParamsForTesting {

  public static final BuildEnvironmentDescription BUILD_ENVIRONMENT_DESCRIPTION =
      BuildEnvironmentDescription.builder()
          .setUser("test")
          .setHostname("test")
          .setOs("test")
          .setAvailableCores(1)
          .setSystemMemory(1024L)
          .setBuckDirty(Optional.of(false))
          .setBuckCommit("test")
          .setJavaVersion("test")
          .setJsonProtocolVersion(1)
          .build();

  /** Utility class: do not instantiate. */
  private CommandRunnerParamsForTesting() {}

  public static CommandRunnerParams createCommandRunnerParamsForTesting(
      Console console,
      Cell cell,
      ArtifactCache artifactCache,
      BuckEventBus eventBus,
      BuckConfig config,
      Platform platform,
      ImmutableMap<String, String> environment,
      JavaPackageFinder javaPackageFinder,
      Optional<WebServer> webServer)
      throws IOException, InterruptedException {
    ProcessExecutor processExecutor = new DefaultProcessExecutor(new TestConsole());
    TypeCoercerFactory typeCoercerFactory = new DefaultTypeCoercerFactory();
    KnownBuildRuleTypesProvider knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                processExecutor,
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));

    return CommandRunnerParams.of(
        console,
        new ByteArrayInputStream("".getBytes("UTF-8")),
        cell,
        new InstrumentedVersionedTargetGraphCache(
            new VersionedTargetGraphCache(), new NoOpCacheStatsTracker()),
        new SingletonArtifactCacheFactory(artifactCache),
        typeCoercerFactory,
        new Parser(
            new BroadcastEventListener(),
            cell.getBuckConfig().getView(ParserConfig.class),
            typeCoercerFactory,
            new ConstructorArgMarshaller(typeCoercerFactory),
            knownBuildRuleTypesProvider,
            new ExecutableFinder()),
        eventBus,
        platform,
        environment,
        javaPackageFinder,
        new DefaultClock(),
        new VersionControlStatsGenerator(new NoOpCmdLineInterface(), Optional.empty()),
        Optional.empty(),
        webServer,
        Optional.empty(),
        config,
        new StackedFileHashCache(ImmutableList.of()),
        ImmutableMap.of(ExecutorPool.PROJECT, MoreExecutors.newDirectExecutorService()),
        new FakeExecutor(),
        BUILD_ENVIRONMENT_DESCRIPTION,
        new ActionGraphCache(config.getMaxActionGraphCacheEntries()),
        knownBuildRuleTypesProvider,
        new BuildInfoStoreManager(),
        Optional.empty(),
        Optional.empty(),
        new DefaultProjectFilesystemFactory(),
        TestRuleKeyConfigurationFactory.create(),
        processExecutor,
        new ExecutableFinder(),
        BuckPluginManagerFactory.createPluginManager());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private ArtifactCache artifactCache = new NoopArtifactCache();
    private Console console = new TestConsole();
    private BuckConfig config = FakeBuckConfig.builder().build();
    private BuckEventBus eventBus = BuckEventBusForTests.newInstance();
    private Platform platform = Platform.detect();
    private ImmutableMap<String, String> environment = ImmutableMap.copyOf(System.getenv());
    private JavaPackageFinder javaPackageFinder = new FakeJavaPackageFinder();
    private Optional<WebServer> webServer = Optional.empty();

    public CommandRunnerParams build() throws IOException, InterruptedException {
      return createCommandRunnerParamsForTesting(
          console,
          new TestCellBuilder().build(),
          artifactCache,
          eventBus,
          config,
          platform,
          environment,
          javaPackageFinder,
          webServer);
    }

    public Builder setConsole(Console console) {
      this.console = console;
      return this;
    }

    public Builder setWebserver(Optional<WebServer> webServer) {
      this.webServer = webServer;
      return this;
    }

    public Builder setArtifactCache(ArtifactCache cache) {
      this.artifactCache = cache;
      return this;
    }
  }
}
