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

package com.facebook.buck.rules;

import com.facebook.buck.rules.keys.DefaultRuleKeyCache;
import com.facebook.buck.rules.keys.RuleKeyDiagnostics;
import com.facebook.buck.rules.keys.RuleKeyFactories;
import com.facebook.buck.rules.keys.TrackedRuleKeyCache;
import com.facebook.buck.rules.keys.config.TestRuleKeyConfigurationFactory;
import com.facebook.buck.step.DefaultStepRunner;
import com.facebook.buck.testutil.DummyFileHashCache;
import com.facebook.buck.util.cache.NoOpCacheStatsTracker;
import com.facebook.buck.util.concurrent.FakeWeightedListeningExecutorService;
import com.facebook.buck.util.concurrent.WeightedListeningExecutorService;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;

/** Handy way to create new {@link CachingBuildEngine} instances for test purposes. */
public class CachingBuildEngineFactory {

  private CachingBuildEngine.BuildMode buildMode = CachingBuildEngine.BuildMode.SHALLOW;
  private CachingBuildEngine.MetadataStorage metadataStorage =
      CachingBuildEngine.MetadataStorage.FILESYSTEM;
  private CachingBuildEngine.DepFiles depFiles = CachingBuildEngine.DepFiles.ENABLED;
  private long maxDepFileCacheEntries = 256L;
  private Optional<Long> artifactCacheSizeLimit = Optional.empty();
  private long inputFileSizeLimit = Long.MAX_VALUE;
  private Optional<RuleKeyFactories> ruleKeyFactories = Optional.empty();
  private CachingBuildEngineDelegate cachingBuildEngineDelegate;
  private WeightedListeningExecutorService executorService;
  private BuildRuleResolver buildRuleResolver;
  private ResourceAwareSchedulingInfo resourceAwareSchedulingInfo =
      ResourceAwareSchedulingInfo.NON_AWARE_SCHEDULING_INFO;
  private boolean logBuildRuleFailuresInline = true;
  private BuildInfoStoreManager buildInfoStoreManager;
  private final RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter;

  public CachingBuildEngineFactory(
      BuildRuleResolver buildRuleResolver,
      BuildInfoStoreManager buildInfoStoreManager,
      RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter) {
    this.remoteBuildRuleCompletionWaiter = remoteBuildRuleCompletionWaiter;
    this.cachingBuildEngineDelegate = new LocalCachingBuildEngineDelegate(new DummyFileHashCache());
    this.executorService = toWeighted(MoreExecutors.newDirectExecutorService());
    this.buildRuleResolver = buildRuleResolver;
    this.buildInfoStoreManager = buildInfoStoreManager;
  }

  public CachingBuildEngineFactory setBuildMode(CachingBuildEngine.BuildMode buildMode) {
    this.buildMode = buildMode;
    return this;
  }

  public CachingBuildEngineFactory setDepFiles(CachingBuildEngine.DepFiles depFiles) {
    this.depFiles = depFiles;
    return this;
  }

  public CachingBuildEngineFactory setMaxDepFileCacheEntries(long maxDepFileCacheEntries) {
    this.maxDepFileCacheEntries = maxDepFileCacheEntries;
    return this;
  }

  public CachingBuildEngineFactory setArtifactCacheSizeLimit(
      Optional<Long> artifactCacheSizeLimit) {
    this.artifactCacheSizeLimit = artifactCacheSizeLimit;
    return this;
  }

  public CachingBuildEngineFactory setCachingBuildEngineDelegate(
      CachingBuildEngineDelegate cachingBuildEngineDelegate) {
    this.cachingBuildEngineDelegate = cachingBuildEngineDelegate;
    return this;
  }

  public CachingBuildEngineFactory setExecutorService(ListeningExecutorService executorService) {
    this.executorService = toWeighted(executorService);
    return this;
  }

  public CachingBuildEngineFactory setExecutorService(
      WeightedListeningExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  public CachingBuildEngineFactory setRuleKeyFactories(RuleKeyFactories ruleKeyFactories) {
    this.ruleKeyFactories = Optional.of(ruleKeyFactories);
    return this;
  }

  public CachingBuildEngineFactory setLogBuildRuleFailuresInline(
      boolean logBuildRuleFailuresInline) {
    this.logBuildRuleFailuresInline = logBuildRuleFailuresInline;
    return this;
  }

  public CachingBuildEngine build() {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver sourcePathResolver = DefaultSourcePathResolver.from(ruleFinder);
    if (ruleKeyFactories.isPresent()) {
      return new CachingBuildEngine(
          cachingBuildEngineDelegate,
          executorService,
          new DefaultStepRunner(),
          buildMode,
          metadataStorage,
          depFiles,
          maxDepFileCacheEntries,
          artifactCacheSizeLimit,
          buildRuleResolver,
          buildInfoStoreManager,
          ruleFinder,
          sourcePathResolver,
          ruleKeyFactories.get(),
          remoteBuildRuleCompletionWaiter,
          resourceAwareSchedulingInfo,
          RuleKeyDiagnostics.nop(),
          logBuildRuleFailuresInline);
    }

    return new CachingBuildEngine(
        cachingBuildEngineDelegate,
        executorService,
        new DefaultStepRunner(),
        buildMode,
        metadataStorage,
        depFiles,
        maxDepFileCacheEntries,
        artifactCacheSizeLimit,
        buildRuleResolver,
        ruleFinder,
        sourcePathResolver,
        buildInfoStoreManager,
        resourceAwareSchedulingInfo,
        logBuildRuleFailuresInline,
        RuleKeyFactories.of(
            TestRuleKeyConfigurationFactory.create(),
            cachingBuildEngineDelegate.getFileHashCache(),
            buildRuleResolver,
            inputFileSizeLimit,
            new TrackedRuleKeyCache<>(new DefaultRuleKeyCache<>(), new NoOpCacheStatsTracker())),
        remoteBuildRuleCompletionWaiter);
  }

  private static WeightedListeningExecutorService toWeighted(ListeningExecutorService service) {
    return new FakeWeightedListeningExecutorService(service);
  }
}
