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

package com.facebook.buck.rules;

import com.facebook.buck.artifact_cache.ArtifactCache;
import com.facebook.buck.artifact_cache.CacheResult;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.RuleKeyCalculationEvent;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.keys.RuleKeyAndInputs;
import com.facebook.buck.rules.keys.RuleKeyDiagnostics;
import com.facebook.buck.rules.keys.RuleKeyFactories;
import com.facebook.buck.rules.keys.SizeLimiter;
import com.facebook.buck.rules.keys.SupportsDependencyFileRuleKey;
import com.facebook.buck.rules.keys.hasher.StringRuleKeyHasher;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.StepRunner;
import com.facebook.buck.util.Scope;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.collect.SortedSets;
import com.facebook.buck.util.concurrent.MoreFutures;
import com.facebook.buck.util.concurrent.ResourceAmounts;
import com.facebook.buck.util.concurrent.WeightedListeningExecutorService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A build engine used to build a {@link BuildRule} which also caches the results. If the current
 * {@link RuleKey} of the build rules matches the one on disk, it does not do any work. It also
 * tries to fetch its output from an {@link ArtifactCache} to avoid doing any computation.
 */
public class CachingBuildEngine implements BuildEngine, Closeable {
  public static final ResourceAmounts CACHE_CHECK_RESOURCE_AMOUNTS = ResourceAmounts.of(0, 0, 1, 1);

  public static final ResourceAmounts RULE_KEY_COMPUTATION_RESOURCE_AMOUNTS =
      ResourceAmounts.of(0, 0, 1, 0);
  public static final ResourceAmounts SCHEDULING_MORE_WORK_RESOURCE_AMOUNTS =
      ResourceAmounts.zero();

  static final String BUILD_RULE_TYPE_CONTEXT_KEY = "build_rule_type";
  static final String STEP_TYPE_CONTEXT_KEY = "step_type";
  private final ConcurrentLinkedQueue<ListenableFuture<Void>> asyncCallbacks;

  static enum StepType {
    BUILD_STEP,
    POST_BUILD_STEP,
    ;
  };

  /** The mode in which to build rules. */
  public enum BuildMode {

    // Perform a shallow build, only locally materializing the bare minimum needed to build the
    // top-level build targets.
    SHALLOW,

    // Perform a deep build, locally materializing all the transitive dependencies of the top-level
    // build targets.
    DEEP,

    // Perform local cache population by only loading all the transitive dependencies of
    // the top-level build targets from the remote cache, without building missing or changed
    // dependencies locally.
    POPULATE_FROM_REMOTE_CACHE,
  }

  /** Whether to use dependency files or not. */
  public enum DepFiles {
    ENABLED,
    DISABLED,
    CACHE,
  }

  public enum MetadataStorage {
    FILESYSTEM,
    SQLITE,
  }

  /**
   * These are the values returned by {@link BuildEngine#build(BuildEngineBuildContext,
   * ExecutionContext, BuildRule)}. This must always return the same value for the build of each
   * target.
   */
  private final ConcurrentMap<BuildTarget, ListenableFuture<BuildResult>> results =
      Maps.newConcurrentMap();

  @Nullable private volatile Throwable firstFailure = null;

  private final CachingBuildEngineDelegate cachingBuildEngineDelegate;

  private final WeightedListeningExecutorService service;
  private final StepRunner stepRunner;
  private final BuildMode buildMode;
  private final MetadataStorage metadataStorage;
  private final DepFiles depFiles;
  private final long maxDepFileCacheEntries;
  private final BuildRuleResolver resolver;
  private final SourcePathRuleFinder ruleFinder;
  private final SourcePathResolver pathResolver;
  private final Optional<Long> artifactCacheSizeLimit;
  private final FileHashCache fileHashCache;
  private final RuleKeyFactories ruleKeyFactories;
  private final ResourceAwareSchedulingInfo resourceAwareSchedulingInfo;

  private final RuleDepsCache ruleDeps;
  private final Optional<UnskippedRulesTracker> unskippedRulesTracker;
  private final BuildRuleDurationTracker buildRuleDurationTracker = new BuildRuleDurationTracker();
  private final RuleKeyDiagnostics<RuleKey, String> defaultRuleKeyDiagnostics;
  private final BuildRulePipelinesRunner pipelinesRunner = new BuildRulePipelinesRunner();
  private final ParallelRuleKeyCalculator<RuleKey> ruleKeyCalculator;

  private final BuildInfoStoreManager buildInfoStoreManager;

  private final boolean consoleLogBuildFailuresInline;

  private final RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter;

  public CachingBuildEngine(
      CachingBuildEngineDelegate cachingBuildEngineDelegate,
      WeightedListeningExecutorService service,
      StepRunner stepRunner,
      BuildMode buildMode,
      MetadataStorage metadataStorage,
      DepFiles depFiles,
      long maxDepFileCacheEntries,
      Optional<Long> artifactCacheSizeLimit,
      final BuildRuleResolver resolver,
      SourcePathRuleFinder ruleFinder,
      SourcePathResolver pathResolver,
      BuildInfoStoreManager buildInfoStoreManager,
      ResourceAwareSchedulingInfo resourceAwareSchedulingInfo,
      boolean consoleLogBuildFailuresInline,
      RuleKeyFactories ruleKeyFactories,
      RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter) {
    this(
        cachingBuildEngineDelegate,
        service,
        stepRunner,
        buildMode,
        metadataStorage,
        depFiles,
        maxDepFileCacheEntries,
        artifactCacheSizeLimit,
        resolver,
        buildInfoStoreManager,
        ruleFinder,
        pathResolver,
        ruleKeyFactories,
        remoteBuildRuleCompletionWaiter,
        resourceAwareSchedulingInfo,
        new RuleKeyDiagnostics<>(
            rule ->
                ruleKeyFactories
                    .getDefaultRuleKeyFactory()
                    .buildForDiagnostics(rule, new StringRuleKeyHasher()),
            appendable ->
                ruleKeyFactories
                    .getDefaultRuleKeyFactory()
                    .buildForDiagnostics(appendable, new StringRuleKeyHasher())),
        consoleLogBuildFailuresInline);
  }

  /** This constructor MUST ONLY BE USED FOR TESTS. */
  @VisibleForTesting
  CachingBuildEngine(
      CachingBuildEngineDelegate cachingBuildEngineDelegate,
      WeightedListeningExecutorService service,
      StepRunner stepRunner,
      BuildMode buildMode,
      MetadataStorage metadataStorage,
      DepFiles depFiles,
      long maxDepFileCacheEntries,
      Optional<Long> artifactCacheSizeLimit,
      BuildRuleResolver resolver,
      BuildInfoStoreManager buildInfoStoreManager,
      SourcePathRuleFinder ruleFinder,
      SourcePathResolver pathResolver,
      RuleKeyFactories ruleKeyFactories,
      RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter,
      ResourceAwareSchedulingInfo resourceAwareSchedulingInfo,
      RuleKeyDiagnostics<RuleKey, String> defaultRuleKeyDiagnostics,
      boolean consoleLogBuildFailuresInline) {
    this.cachingBuildEngineDelegate = cachingBuildEngineDelegate;

    this.service = service;
    this.stepRunner = stepRunner;
    this.buildMode = buildMode;
    this.metadataStorage = metadataStorage;
    this.depFiles = depFiles;
    this.maxDepFileCacheEntries = maxDepFileCacheEntries;
    this.artifactCacheSizeLimit = artifactCacheSizeLimit;
    this.resolver = resolver;
    this.ruleFinder = ruleFinder;
    this.pathResolver = pathResolver;

    this.fileHashCache = cachingBuildEngineDelegate.getFileHashCache();
    this.ruleKeyFactories = ruleKeyFactories;
    this.resourceAwareSchedulingInfo = resourceAwareSchedulingInfo;
    this.buildInfoStoreManager = buildInfoStoreManager;
    this.remoteBuildRuleCompletionWaiter = remoteBuildRuleCompletionWaiter;

    this.ruleDeps = new RuleDepsCache(resolver);
    this.unskippedRulesTracker = createUnskippedRulesTracker(buildMode, ruleDeps, resolver);
    this.defaultRuleKeyDiagnostics = defaultRuleKeyDiagnostics;
    this.consoleLogBuildFailuresInline = consoleLogBuildFailuresInline;
    this.asyncCallbacks = new ConcurrentLinkedQueue<>();
    this.ruleKeyCalculator =
        new ParallelRuleKeyCalculator<>(
            serviceByAdjustingDefaultWeightsTo(RULE_KEY_COMPUTATION_RESOURCE_AMOUNTS),
            ruleKeyFactories.getDefaultRuleKeyFactory(),
            ruleDeps,
            (eventBus, rule) ->
                BuildRuleEvent.ruleKeyCalculationScope(
                    eventBus,
                    rule,
                    buildRuleDurationTracker,
                    ruleKeyFactories.getDefaultRuleKeyFactory()));
  }

  @Override
  public void close() {
    try {
      Futures.allAsList(asyncCallbacks).get();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /// We might want to share rule-key calculation with other parts of code.
  public ParallelRuleKeyCalculator<RuleKey> getRuleKeyCalculator() {
    return ruleKeyCalculator;
  }

  /**
   * We have a lot of places where tasks are submitted into a service implicitly. There is no way to
   * assign custom weights to such tasks. By creating a temporary service with adjusted weights it
   * is possible to trick the system and tweak the weights.
   */
  private WeightedListeningExecutorService serviceByAdjustingDefaultWeightsTo(
      ResourceAmounts defaultAmounts) {
    return resourceAwareSchedulingInfo.adjustServiceDefaultWeightsTo(defaultAmounts, service);
  }

  private static Optional<UnskippedRulesTracker> createUnskippedRulesTracker(
      BuildMode buildMode, RuleDepsCache ruleDeps, BuildRuleResolver resolver) {
    if (buildMode == BuildMode.DEEP || buildMode == BuildMode.POPULATE_FROM_REMOTE_CACHE) {
      // Those modes never skip rules, there is no need to track unskipped rules.
      return Optional.empty();
    }
    return Optional.of(new UnskippedRulesTracker(ruleDeps, resolver));
  }

  @VisibleForTesting
  void setBuildRuleResult(
      BuildRule buildRule, BuildRuleSuccessType success, CacheResult cacheResult) {
    results.put(
        buildRule.getBuildTarget(),
        Futures.immediateFuture(BuildResult.success(buildRule, success, cacheResult)));
  }

  @Override
  public boolean isRuleBuilt(BuildTarget buildTarget) throws InterruptedException {
    ListenableFuture<BuildResult> resultFuture = results.get(buildTarget);
    return resultFuture != null && MoreFutures.isSuccess(resultFuture);
  }

  @Override
  public void terminateBuildWithFailure(Throwable failure) {
    firstFailure = failure;
  }

  // Dispatch and return a future resolving to a list of all results of this rules dependencies.
  private ListenableFuture<List<BuildResult>> getDepResults(
      BuildRule rule, BuildEngineBuildContext buildContext, ExecutionContext executionContext) {
    List<ListenableFuture<BuildResult>> depResults =
        new ArrayList<>(SortedSets.sizeEstimate(rule.getBuildDeps()));
    for (BuildRule dep : shuffled(rule.getBuildDeps())) {
      depResults.add(getBuildRuleResultWithRuntimeDeps(dep, buildContext, executionContext));
    }
    return Futures.allAsList(depResults);
  }

  private static List<BuildRule> shuffled(Iterable<BuildRule> rules) {
    ArrayList<BuildRule> rulesList = Lists.newArrayList(rules);
    Collections.shuffle(rulesList);
    return rulesList;
  }

  private void registerTopLevelRule(BuildRule rule, BuckEventBus eventBus) {
    unskippedRulesTracker.ifPresent(tracker -> tracker.registerTopLevelRule(rule, eventBus));
  }

  private void markRuleAsUsed(BuildRule rule, BuckEventBus eventBus) {
    unskippedRulesTracker.ifPresent(tracker -> tracker.markRuleAsUsed(rule, eventBus));
  }

  // Provide a future that resolves to the result of executing this rule and its runtime
  // dependencies.
  private ListenableFuture<BuildResult> getBuildRuleResultWithRuntimeDepsUnlocked(
      final BuildRule rule,
      final BuildEngineBuildContext buildContext,
      final ExecutionContext executionContext) {

    // If the rule is already executing, return its result future from the cache.
    ListenableFuture<BuildResult> existingResult = results.get(rule.getBuildTarget());
    if (existingResult != null) {
      return existingResult;
    }

    // Get the future holding the result for this rule and, if we have no additional runtime deps
    // to attach, return it.
    ListenableFuture<RuleKey> ruleKey = calculateRuleKey(rule, buildContext);
    ListenableFuture<BuildResult> result =
        Futures.transformAsync(
            ruleKey,
            input -> processBuildRule(rule, buildContext, executionContext),
            serviceByAdjustingDefaultWeightsTo(SCHEDULING_MORE_WORK_RESOURCE_AMOUNTS));
    if (!(rule instanceof HasRuntimeDeps)) {
      results.put(rule.getBuildTarget(), result);
      return result;
    }

    // Collect any runtime deps we have into a list of futures.
    Stream<BuildTarget> runtimeDepPaths = ((HasRuntimeDeps) rule).getRuntimeDeps(ruleFinder);
    List<ListenableFuture<BuildResult>> runtimeDepResults = new ArrayList<>();
    ImmutableSet<BuildRule> runtimeDeps =
        resolver.getAllRules(runtimeDepPaths.collect(ImmutableSet.toImmutableSet()));
    for (BuildRule dep : runtimeDeps) {
      runtimeDepResults.add(
          getBuildRuleResultWithRuntimeDepsUnlocked(dep, buildContext, executionContext));
    }

    // Create a new combined future, which runs the original rule and all the runtime deps in
    // parallel, but which propagates an error if any one of them fails.
    // It also checks that all runtime deps succeeded.
    ListenableFuture<BuildResult> chainedResult =
        Futures.transformAsync(
            Futures.allAsList(runtimeDepResults),
            results ->
                !buildContext.isKeepGoing() && firstFailure != null
                    ? Futures.immediateFuture(BuildResult.canceled(rule, firstFailure))
                    : result,
            MoreExecutors.directExecutor());
    results.put(rule.getBuildTarget(), chainedResult);
    return chainedResult;
  }

  private ListenableFuture<BuildResult> getBuildRuleResultWithRuntimeDeps(
      BuildRule rule, BuildEngineBuildContext buildContext, ExecutionContext executionContext) {

    // If the rule is already executing, return it's result future from the cache without acquiring
    // the lock.
    ListenableFuture<BuildResult> existingResult = results.get(rule.getBuildTarget());
    if (existingResult != null) {
      return existingResult;
    }

    // Otherwise, grab the lock and delegate to the real method,
    synchronized (results) {
      return getBuildRuleResultWithRuntimeDepsUnlocked(rule, buildContext, executionContext);
    }
  }

  public ListenableFuture<?> walkRule(BuildRule rule, final Set<BuildRule> seen) {
    return Futures.transformAsync(
        Futures.immediateFuture(ruleDeps.get(rule)),
        deps -> {
          List<ListenableFuture<?>> results1 = new ArrayList<>(SortedSets.sizeEstimate(deps));
          for (BuildRule dep : deps) {
            if (seen.add(dep)) {
              results1.add(walkRule(dep, seen));
            }
          }
          return Futures.allAsList(results1);
        },
        serviceByAdjustingDefaultWeightsTo(SCHEDULING_MORE_WORK_RESOURCE_AMOUNTS));
  }

  @Override
  public int getNumRulesToBuild(Iterable<BuildRule> rules) {
    Set<BuildRule> seen = Sets.newConcurrentHashSet();
    ImmutableList.Builder<ListenableFuture<?>> results = ImmutableList.builder();
    for (final BuildRule rule : rules) {
      if (seen.add(rule)) {
        results.add(walkRule(rule, seen));
      }
    }
    Futures.getUnchecked(Futures.allAsList(results.build()));
    return seen.size();
  }

  private ListenableFuture<RuleKey> calculateRuleKey(
      BuildRule rule, BuildEngineBuildContext context) {
    return ruleKeyCalculator.calculate(context.getEventBus(), rule);
  }

  @Override
  public BuildEngineResult build(
      BuildEngineBuildContext buildContext, ExecutionContext executionContext, BuildRule rule) {
    // Keep track of all jobs that run asynchronously with respect to the build dep chain.  We want
    // to make sure we wait for these before calling yielding the final build result.
    registerTopLevelRule(rule, buildContext.getEventBus());
    ListenableFuture<BuildResult> resultFuture =
        getBuildRuleResultWithRuntimeDeps(rule, buildContext, executionContext);
    return BuildEngineResult.builder().setResult(resultFuture).build();
  }

  @Nullable
  @Override
  public BuildResult getBuildRuleResult(BuildTarget buildTarget)
      throws ExecutionException, InterruptedException {
    ListenableFuture<BuildResult> result = results.get(buildTarget);
    if (result == null) {
      return null;
    }
    return result.get();
  }

  private boolean shouldKeepGoing(BuildEngineBuildContext buildContext) {
    boolean keepGoing =
        firstFailure == null
            || buildMode == BuildMode.POPULATE_FROM_REMOTE_CACHE
            || buildContext.isKeepGoing();

    if (!keepGoing) {
      // Ensure any pending/future cache fetch requests are not processed.
      // Note: these are processed on a different Executor from the one used by the build engine.
      buildContext.getArtifactCache().skipPendingAndFutureAsyncFetches();
    }

    return keepGoing;
  }

  @VisibleForTesting
  public Optional<RuleKey> getManifestRuleKeyForTest(
      SupportsDependencyFileRuleKey rule, BuckEventBus eventBus) throws IOException {
    return calculateManifestKey(rule, eventBus, ruleKeyFactories).map(RuleKeyAndInputs::getRuleKey);
  }

  static Optional<RuleKeyAndInputs> calculateManifestKey(
      SupportsDependencyFileRuleKey rule, BuckEventBus eventBus, RuleKeyFactories ruleKeyFactories)
      throws IOException {
    try (Scope scope =
        RuleKeyCalculationEvent.scope(
            eventBus, RuleKeyCalculationEvent.Type.MANIFEST, rule.getBuildTarget())) {
      return Optional.of(ruleKeyFactories.getDepFileRuleKeyFactory().buildManifestKey(rule));
    } catch (SizeLimiter.SizeLimitException ex) {
      return Optional.empty();
    }
  }

  private ListenableFuture<BuildResult> processBuildRule(
      BuildRule rule, BuildEngineBuildContext buildContext, ExecutionContext executionContext) {

    final BuildInfoStore buildInfoStore =
        buildInfoStoreManager.get(rule.getProjectFilesystem(), metadataStorage);
    final OnDiskBuildInfo onDiskBuildInfo =
        buildContext.createOnDiskBuildInfoFor(
            rule.getBuildTarget(), rule.getProjectFilesystem(), buildInfoStore);
    final BuildInfoRecorder buildInfoRecorder =
        buildContext
            .createBuildInfoRecorder(
                rule.getBuildTarget(), rule.getProjectFilesystem(), buildInfoStore)
            .addBuildMetadata(
                BuildInfo.MetadataKey.RULE_KEY,
                ruleKeyFactories.getDefaultRuleKeyFactory().build(rule).toString())
            .addBuildMetadata(BuildInfo.MetadataKey.BUILD_ID, buildContext.getBuildId().toString());
    final BuildableContext buildableContext = new DefaultBuildableContext(buildInfoRecorder);
    return new CachingBuildRuleBuilder(
            new DefaultBuildRuleBuilderDelegate(this, buildContext),
            artifactCacheSizeLimit,
            buildInfoStoreManager,
            buildMode,
            buildRuleDurationTracker,
            consoleLogBuildFailuresInline,
            defaultRuleKeyDiagnostics,
            depFiles,
            fileHashCache,
            maxDepFileCacheEntries,
            metadataStorage,
            pathResolver,
            resourceAwareSchedulingInfo,
            ruleKeyFactories,
            service,
            stepRunner,
            this.ruleDeps,
            rule,
            buildContext,
            executionContext,
            onDiskBuildInfo,
            buildInfoRecorder,
            buildableContext,
            pipelinesRunner,
            remoteBuildRuleCompletionWaiter)
        .build();
  }

  public static class DefaultBuildRuleBuilderDelegate
      implements CachingBuildRuleBuilder.BuildRuleBuilderDelegate {
    private final CachingBuildEngine cachingBuildEngine;
    private final BuildEngineBuildContext buildContext;

    public DefaultBuildRuleBuilderDelegate(
        CachingBuildEngine cachingBuildEngine, BuildEngineBuildContext buildContext) {
      this.cachingBuildEngine = cachingBuildEngine;
      this.buildContext = buildContext;
    }

    @Override
    public void markRuleAsUsed(BuildRule rule, BuckEventBus eventBus) {
      cachingBuildEngine.markRuleAsUsed(rule, eventBus);
    }

    @Override
    public boolean shouldKeepGoing() {
      return cachingBuildEngine.shouldKeepGoing(buildContext);
    }

    @Override
    public void setFirstFailure(Throwable throwable) {
      cachingBuildEngine.firstFailure = throwable;
    }

    @Override
    public ListenableFuture<List<BuildResult>> getDepResults(
        BuildRule rule, ExecutionContext executionContext) {
      return cachingBuildEngine.getDepResults(rule, buildContext, executionContext);
    }

    @Override
    public void addAsyncCallback(ListenableFuture<Void> callback) {
      cachingBuildEngine.asyncCallbacks.add(callback);
    }

    @Override
    @Nullable
    public Throwable getFirstFailure() {
      return cachingBuildEngine.firstFailure;
    }

    @Override
    public void onRuleAboutToBeBuilt(BuildRule rule) {
      cachingBuildEngine.cachingBuildEngineDelegate.onRuleAboutToBeBuilt(rule);
    }
  }
}
