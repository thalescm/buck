/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.distributed.build_slave;

import com.facebook.buck.artifact_cache.ArtifactCacheFactory;
import com.facebook.buck.command.BuildExecutorArgs;
import com.facebook.buck.config.resources.ResourcesConfig;
import com.facebook.buck.distributed.DistBuildConfig;
import com.facebook.buck.distributed.DistBuildMode;
import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.DistBuildState;
import com.facebook.buck.distributed.FileContentsProvider;
import com.facebook.buck.distributed.thrift.BuildSlaveRunId;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.io.filesystem.ProjectFilesystemFactory;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.rules.ActionGraphCache;
import com.facebook.buck.rules.BuildInfoStoreManager;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.KnownBuildRuleTypesProvider;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.keys.RuleKeyCacheScope;
import com.facebook.buck.rules.keys.config.RuleKeyConfiguration;
import com.facebook.buck.step.ExecutorPool;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.concurrent.WeightedListeningExecutorService;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.util.timing.Clock;
import com.facebook.buck.versions.InstrumentedVersionedTargetGraphCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.file.Path;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
abstract class AbstractDistBuildSlaveExecutorArgs {
  public abstract DistBuildState getState();

  public Cell getRootCell() {
    return getState().getRootCell();
  }

  public abstract Parser getParser();

  public abstract BuckEventBus getBuckEventBus();

  public abstract WeightedListeningExecutorService getExecutorService();

  public abstract ActionGraphCache getActionGraphCache();

  public abstract RuleKeyConfiguration getRuleKeyConfiguration();

  public abstract Console getConsole();

  public abstract ArtifactCacheFactory getArtifactCacheFactory();

  public abstract Path getLogDirectoryPath();

  public abstract Platform getPlatform();

  public abstract Clock getClock();

  public abstract Map<ExecutorPool, ListeningExecutorService> getExecutors();

  public abstract FileContentsProvider getProvider();

  public abstract DistBuildMode getDistBuildMode();

  public abstract int getRemoteCoordinatorPort();

  public abstract StampedeId getStampedeId();

  public abstract BuildSlaveRunId getBuildSlaveRunId();

  public abstract String getRemoteCoordinatorAddress();

  public abstract InstrumentedVersionedTargetGraphCache getVersionedTargetGraphCache();

  public abstract BuildInfoStoreManager getBuildInfoStoreManager();

  public abstract DistBuildService getDistBuildService();

  public abstract RuleKeyCacheScope<RuleKey> getRuleKeyCacheScope();

  public DistBuildConfig getDistBuildConfig() {
    return new DistBuildConfig(getState().getRemoteRootCellConfig());
  }

  public abstract ProjectFilesystemFactory getProjectFilesystemFactory();

  public abstract KnownBuildRuleTypesProvider getKnownBuildRuleTypesProvider();

  public abstract CoordinatorBuildRuleEventsPublisher getCoordinatorBuildRuleEventsPublisher();

  public abstract MinionBuildProgressTracker getMinionBuildProgressTracker();

  public abstract HealthCheckStatsTracker getHealthCheckStatsTracker();

  public int getBuildThreadCount() {
    return getState()
        .getRemoteRootCellConfig()
        .getView(ResourcesConfig.class)
        .getConcurrencyLimit()
        .threadLimit;
  }

  /**
   * Create {@link BuildExecutorArgs} using {@link DistBuildSlaveExecutorArgs}.
   *
   * @return New instance of {@link BuildExecutorArgs}.
   */
  public BuildExecutorArgs createBuilderArgs() {
    return BuildExecutorArgs.builder()
        .setConsole(getConsole())
        .setBuckEventBus(getBuckEventBus())
        .setPlatform(getPlatform())
        .setClock(getClock())
        .setRootCell(getRootCell())
        .setExecutors(getExecutors())
        .setProjectFilesystemFactory(getProjectFilesystemFactory())
        .setBuildInfoStoreManager(getBuildInfoStoreManager())
        .setArtifactCacheFactory(getArtifactCacheFactory())
        .setRuleKeyConfiguration(getRuleKeyConfiguration())
        .build();
  }

  /** Create the arguments for a new instance of DelegateAndGraphsInitiazer. */
  public DelegateAndGraphsInitializerArgs createDelegateAndGraphsInitiazerArgs() {
    return DelegateAndGraphsInitializerArgs.builder()
        .setState(this.getState())
        .setTimingStatsTracker(this.getTimingStatsTracker())
        .setVersionedTargetGraphCache(this.getVersionedTargetGraphCache())
        .setActionGraphCache(this.getActionGraphCache())
        .setParser(this.getParser())
        .setBuckEventBus(this.getBuckEventBus())
        .setRuleKeyConfiguration(this.getRuleKeyConfiguration())
        .setProjectFilesystemFactory(this.getProjectFilesystemFactory())
        .setExecutorService(this.getExecutorService())
        .setExecutors(this.getExecutors())
        .setProvider(this.getProvider())
        .setKnownBuildRuleTypesProvider(this.getKnownBuildRuleTypesProvider())
        .setShouldInstrumentActionGraph(
            this.getDistBuildConfig().getBuckConfig().getShouldInstrumentActionGraph())
        .build();
  }

  public abstract BuildSlaveTimingStatsTracker getTimingStatsTracker();
}
