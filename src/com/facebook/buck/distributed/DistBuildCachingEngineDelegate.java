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

package com.facebook.buck.distributed;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.CachingBuildEngineDelegate;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.rules.keys.config.RuleKeyConfiguration;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.cache.impl.StackedFileHashCache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of {@link CachingBuildEngineDelegate} for use when building from a state file in
 * distributed build.
 */
public class DistBuildCachingEngineDelegate implements CachingBuildEngineDelegate {
  private static final long DEFAULT_PENDING_FILE_MATERIALIZATION_TIMEOUT_SECONDS = 30;

  private final StackedFileHashCache remoteStackedFileHashCache;
  private final ImmutableList<MaterializerDummyFileHashCache> materializerFileHashCaches;
  private final LoadingCache<ProjectFilesystem, DefaultRuleKeyFactory>
      materializingRuleKeyFactories;
  private final long pendingFileMaterializationTimeoutSeconds;

  public DistBuildCachingEngineDelegate(
      SourcePathResolver sourcePathResolver,
      SourcePathRuleFinder ruleFinder,
      StackedFileHashCache remoteStackedFileHashCache,
      StackedFileHashCache materializingStackedFileHashCache,
      RuleKeyConfiguration ruleKeyConfiguration,
      long pendingFileMaterializationTimeoutSeconds) {
    this.remoteStackedFileHashCache = remoteStackedFileHashCache;
    this.pendingFileMaterializationTimeoutSeconds = pendingFileMaterializationTimeoutSeconds;
    this.materializingRuleKeyFactories =
        DistBuildFileHashes.createRuleKeyFactories(
            sourcePathResolver,
            ruleFinder,
            materializingStackedFileHashCache,
            ruleKeyConfiguration);

    this.materializerFileHashCaches =
        materializingStackedFileHashCache
            .getCaches()
            .stream()
            .filter(cache -> cache instanceof MaterializerDummyFileHashCache)
            .map(cache -> (MaterializerDummyFileHashCache) cache)
            .collect(ImmutableList.toImmutableList());
  }

  /**
   * @param sourcePathResolver Distributed build source parse resolver.
   * @param ruleFinder Used by the distributed build rule key factories.
   * @param remoteStackedFileHashCache Cache that only requires SHA1.
   * @param materializingStackedFileHashCache Cache that writes the files to the disk.
   */
  public DistBuildCachingEngineDelegate(
      SourcePathResolver sourcePathResolver,
      SourcePathRuleFinder ruleFinder,
      StackedFileHashCache remoteStackedFileHashCache,
      StackedFileHashCache materializingStackedFileHashCache,
      RuleKeyConfiguration ruleKeyConfiguration) {
    this(
        sourcePathResolver,
        ruleFinder,
        remoteStackedFileHashCache,
        materializingStackedFileHashCache,
        ruleKeyConfiguration,
        DEFAULT_PENDING_FILE_MATERIALIZATION_TIMEOUT_SECONDS);
  }

  @Override
  public FileHashCache getFileHashCache() {
    return remoteStackedFileHashCache;
  }

  @Override
  public void onRuleAboutToBeBuilt(BuildRule buildRule) {
    try {
      materializingRuleKeyFactories.get(buildRule.getProjectFilesystem()).build(buildRule);
      waitForPendingFileMaterialization();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void waitForPendingFileMaterialization() {
    List<ListenableFuture<?>> fileMaterializationFutures =
        new ArrayList<>(materializerFileHashCaches.size());
    for (MaterializerDummyFileHashCache cache : materializerFileHashCaches) {
      fileMaterializationFutures.add(cache.getMaterializationFuturesAsList());
    }

    ListenableFuture<?> pendingFilesFuture = Futures.allAsList(fileMaterializationFutures);

    try {
      pendingFilesFuture.get(pendingFileMaterializationTimeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(
          String.format(
              "Unexpected error encountered while waiting for files to be materialized: [%s].",
              e.getMessage()),
          e);
    }
  }
}
