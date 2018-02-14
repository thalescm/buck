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

package com.facebook.buck.rules;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.artifact_cache.ArtifactCache;
import com.facebook.buck.artifact_cache.ArtifactInfo;
import com.facebook.buck.artifact_cache.CacheDeleteResult;
import com.facebook.buck.artifact_cache.CacheResult;
import com.facebook.buck.artifact_cache.CacheResultType;
import com.facebook.buck.artifact_cache.InMemoryArtifactCache;
import com.facebook.buck.artifact_cache.NoopArtifactCache;
import com.facebook.buck.artifact_cache.config.ArtifactCacheMode;
import com.facebook.buck.artifact_cache.config.CacheReadMode;
import com.facebook.buck.cli.CommandThreadManager;
import com.facebook.buck.event.BuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.CommandEvent;
import com.facebook.buck.event.FakeBuckEventListener;
import com.facebook.buck.event.TestEventConfigurator;
import com.facebook.buck.file.WriteFile;
import com.facebook.buck.io.file.BorrowablePath;
import com.facebook.buck.io.file.LazyPath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.keys.DefaultDependencyFileRuleKeyFactory;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.rules.keys.DependencyFileEntry;
import com.facebook.buck.rules.keys.DependencyFileRuleKeyFactory;
import com.facebook.buck.rules.keys.FakeRuleKeyFactory;
import com.facebook.buck.rules.keys.InputBasedRuleKeyFactory;
import com.facebook.buck.rules.keys.RuleKeyAndInputs;
import com.facebook.buck.rules.keys.RuleKeyFactories;
import com.facebook.buck.rules.keys.RuleKeyFieldLoader;
import com.facebook.buck.rules.keys.SupportsDependencyFileRuleKey;
import com.facebook.buck.rules.keys.SupportsInputBasedRuleKey;
import com.facebook.buck.rules.keys.TestInputBasedRuleKeyFactory;
import com.facebook.buck.rules.keys.config.TestRuleKeyConfigurationFactory;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.StepFailedException;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.step.fs.WriteFileStep;
import com.facebook.buck.testutil.DummyFileHashCache;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.ObjectMappers;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.cache.FileHashCacheMode;
import com.facebook.buck.util.cache.impl.StackedFileHashCache;
import com.facebook.buck.util.concurrent.ConcurrencyLimit;
import com.facebook.buck.util.concurrent.ListeningMultiSemaphore;
import com.facebook.buck.util.concurrent.MoreFutures;
import com.facebook.buck.util.concurrent.ResourceAllocationFairness;
import com.facebook.buck.util.concurrent.ResourceAmounts;
import com.facebook.buck.util.concurrent.WeightedListeningExecutorService;
import com.facebook.buck.util.exceptions.BuckUncheckedExecutionException;
import com.facebook.buck.util.exceptions.ExceptionWithContext;
import com.facebook.buck.util.timing.DefaultClock;
import com.facebook.buck.util.timing.IncrementingFakeClock;
import com.facebook.buck.util.zip.CustomZipEntry;
import com.facebook.buck.util.zip.CustomZipOutputStream;
import com.facebook.buck.util.zip.ZipConstants;
import com.facebook.buck.util.zip.ZipOutputStreams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.AbstractListeningExecutorService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.easymock.EasyMockSupport;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Ensuring that build rule caching works correctly in Buck is imperative for both its performance
 * and correctness.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@RunWith(Enclosed.class)
public class CachingBuildEngineTest {
  private static final boolean DEBUG = false;
  private static final BuildTarget BUILD_TARGET =
      BuildTargetFactory.newInstance("//src/com/facebook/orca:orca");
  private static final SourcePathRuleFinder DEFAULT_RULE_FINDER =
      new SourcePathRuleFinder(
          new SingleThreadedBuildRuleResolver(
              TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
  private static final SourcePathResolver DEFAULT_SOURCE_PATH_RESOLVER =
      DefaultSourcePathResolver.from(DEFAULT_RULE_FINDER);
  private static final long NO_INPUT_FILE_SIZE_LIMIT = Long.MAX_VALUE;
  private static final RuleKeyFieldLoader FIELD_LOADER =
      new RuleKeyFieldLoader(TestRuleKeyConfigurationFactory.create());
  private static final DefaultRuleKeyFactory NOOP_RULE_KEY_FACTORY =
      new DefaultRuleKeyFactory(
          FIELD_LOADER,
          new DummyFileHashCache(),
          DEFAULT_SOURCE_PATH_RESOLVER,
          DEFAULT_RULE_FINDER);
  private static final InputBasedRuleKeyFactory NOOP_INPUT_BASED_RULE_KEY_FACTORY =
      new TestInputBasedRuleKeyFactory(
          FIELD_LOADER,
          new DummyFileHashCache(),
          DEFAULT_SOURCE_PATH_RESOLVER,
          DEFAULT_RULE_FINDER,
          NO_INPUT_FILE_SIZE_LIMIT);
  private static final DependencyFileRuleKeyFactory NOOP_DEP_FILE_RULE_KEY_FACTORY =
      new DefaultDependencyFileRuleKeyFactory(
          FIELD_LOADER,
          new DummyFileHashCache(),
          DEFAULT_SOURCE_PATH_RESOLVER,
          DEFAULT_RULE_FINDER);

  @RunWith(Parameterized.class)
  public abstract static class CommonFixture extends EasyMockSupport {
    @Rule public TemporaryPaths tmp = new TemporaryPaths();

    protected final InMemoryArtifactCache cache = new InMemoryArtifactCache();
    protected final FakeBuckEventListener listener = new FakeBuckEventListener();
    protected ProjectFilesystem filesystem;
    protected BuildInfoStoreManager buildInfoStoreManager;
    protected BuildInfoStore buildInfoStore;
    protected RemoteBuildRuleCompletionWaiter defaultRemoteBuildRuleCompletionWaiter;
    protected FileHashCache fileHashCache;
    protected BuildEngineBuildContext buildContext;
    protected BuildRuleResolver resolver;
    protected SourcePathRuleFinder ruleFinder;
    protected SourcePathResolver pathResolver;
    protected DefaultRuleKeyFactory defaultRuleKeyFactory;
    protected InputBasedRuleKeyFactory inputBasedRuleKeyFactory;
    protected BuildRuleDurationTracker durationTracker;
    protected CachingBuildEngine.MetadataStorage metadataStorage;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
      return Arrays.stream(CachingBuildEngine.MetadataStorage.values())
          .map(v -> new Object[] {v})
          .collect(ImmutableList.toImmutableList());
    }

    public CommonFixture(CachingBuildEngine.MetadataStorage metadataStorage) throws IOException {
      this.metadataStorage = metadataStorage;
    }

    @Before
    public void setUp() throws Exception {
      filesystem = TestProjectFilesystems.createProjectFilesystem(tmp.getRoot());
      buildInfoStoreManager = new BuildInfoStoreManager();
      Files.createDirectories(filesystem.resolve(filesystem.getBuckPaths().getScratchDir()));
      buildInfoStore = buildInfoStoreManager.get(filesystem, metadataStorage);
      defaultRemoteBuildRuleCompletionWaiter = new NoOpRemoteBuildRuleCompletionWaiter();
      fileHashCache =
          StackedFileHashCache.createDefaultHashCaches(filesystem, FileHashCacheMode.DEFAULT);
      buildContext =
          BuildEngineBuildContext.builder()
              .setBuildContext(FakeBuildContext.NOOP_CONTEXT)
              .setArtifactCache(cache)
              .setBuildId(new BuildId())
              .setClock(new IncrementingFakeClock())
              .build();
      buildContext.getEventBus().register(listener);
      resolver =
          new SingleThreadedBuildRuleResolver(
              TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
      ruleFinder = new SourcePathRuleFinder(resolver);
      pathResolver = DefaultSourcePathResolver.from(ruleFinder);
      defaultRuleKeyFactory =
          new DefaultRuleKeyFactory(FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);
      inputBasedRuleKeyFactory =
          new TestInputBasedRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder, NO_INPUT_FILE_SIZE_LIMIT);
      durationTracker = new BuildRuleDurationTracker();
    }

    protected CachingBuildEngineFactory cachingBuildEngineFactory() {
      return cachingBuildEngineFactory(defaultRemoteBuildRuleCompletionWaiter);
    }

    protected CachingBuildEngineFactory cachingBuildEngineFactory(
        RemoteBuildRuleCompletionWaiter remoteBuildRuleCompletionWaiter) {
      return new CachingBuildEngineFactory(
              resolver, buildInfoStoreManager, remoteBuildRuleCompletionWaiter)
          .setCachingBuildEngineDelegate(new LocalCachingBuildEngineDelegate(fileHashCache));
    }

    protected BuildInfoRecorder createBuildInfoRecorder(BuildTarget buildTarget) {
      return new BuildInfoRecorder(
          buildTarget,
          filesystem,
          buildInfoStore,
          new DefaultClock(),
          new BuildId(),
          ImmutableMap.of());
    }
  }

  public static class OtherTests extends CommonFixture {
    public OtherTests(CachingBuildEngine.MetadataStorage metadataStorage) throws IOException {
      super(metadataStorage);
    }

    /**
     * Tests what should happen when a rule is built for the first time: it should have no cached
     * RuleKey, nor should it have any artifact in the ArtifactCache. The sequence of events should
     * be as follows:
     *
     * <ol>
     *   <li>The build engine invokes the {@link CachingBuildEngine#build(BuildEngineBuildContext,
     *       ExecutionContext, BuildRule)} method on each of the transitive deps.
     *   <li>The rule computes its own {@link RuleKey}.
     *   <li>The engine compares its {@link RuleKey} to the one on disk, if present.
     *   <li>Because the rule has no {@link RuleKey} on disk, the engine tries to build the rule.
     *   <li>First, it checks the artifact cache, but there is a cache miss.
     *   <li>The rule generates its build steps and the build engine executes them.
     *   <li>Upon executing its steps successfully, the build engine should write the rule's {@link
     *       RuleKey} to disk.
     *   <li>The build engine should persist a rule's output to the ArtifactCache.
     * </ol>
     */
    @Test
    public void testBuildRuleLocallyWithCacheMiss()
        throws IOException, InterruptedException, ExecutionException, StepFailedException {
      // Create a dep for the build rule.
      BuildTarget depTarget = BuildTargetFactory.newInstance("//src/com/facebook/orca:lib");
      FakeBuildRule dep = new FakeBuildRule(depTarget);

      // The EventBus should be updated with events indicating how the rule was built.
      BuckEventBus buckEventBus = BuckEventBusForTests.newInstance();
      FakeBuckEventListener listener = new FakeBuckEventListener();
      buckEventBus.register(listener);

      // Replay the mocks to instantiate the AbstractCachingBuildRule.
      replayAll();
      String pathToOutputFile = "buck-out/gen/src/com/facebook/orca/some_file";
      List<Step> buildSteps = new ArrayList<>();
      final BuildRule ruleToTest =
          createRule(
              filesystem,
              resolver,
              ImmutableSortedSet.of(dep),
              buildSteps,
              /* postBuildSteps */ ImmutableList.of(),
              pathToOutputFile,
              ImmutableList.of());
      verifyAll();
      resetAll();

      // The BuildContext that will be used by the rule's build() method.
      BuildEngineBuildContext buildContext =
          this.buildContext.withBuildContext(
              this.buildContext.getBuildContext().withEventBus(buckEventBus));

      CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build();

      // Add a build step so we can verify that the steps are executed.
      buildSteps.add(
          new AbstractExecutionStep("Some Short Name") {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              Path outputPath = pathResolver.getRelativePath(ruleToTest.getSourcePathToOutput());
              filesystem.mkdirs(outputPath.getParent());
              filesystem.touch(outputPath);
              return StepExecutionResults.SUCCESS;
            }
          });

      // Attempting to build the rule should force a rebuild due to a cache miss.
      replayAll();

      cachingBuildEngine.setBuildRuleResult(
          dep, BuildRuleSuccessType.FETCHED_FROM_CACHE, CacheResult.miss());

      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), ruleToTest)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());
      buckEventBus.post(
          CommandEvent.finished(
              CommandEvent.started("build", ImmutableList.of(), false, 23L), ExitCode.SUCCESS));
      verifyAll();

      RuleKey ruleToTestKey = defaultRuleKeyFactory.build(ruleToTest);
      assertTrue(cache.hasArtifact(ruleToTestKey));

      // Verify the events logged to the BuckEventBus.
      List<BuckEvent> events = listener.getEvents();
      assertThat(events, hasItem(BuildRuleEvent.ruleKeyCalculationStarted(dep, durationTracker)));
      BuildRuleEvent.Started started =
          TestEventConfigurator.configureTestEvent(
              BuildRuleEvent.ruleKeyCalculationStarted(ruleToTest, durationTracker));
      assertThat(
          listener.getEvents(),
          Matchers.containsInRelativeOrder(
              started,
              BuildRuleEvent.finished(
                  started,
                  BuildRuleKeys.of(ruleToTestKey),
                  BuildRuleStatus.SUCCESS,
                  CacheResult.miss(),
                  Optional.empty(),
                  Optional.of(BuildRuleSuccessType.BUILT_LOCALLY),
                  false,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty())));
    }

    @Test
    public void testAsyncJobsAreNotLeftInExecutor()
        throws IOException, ExecutionException, InterruptedException {
      BuildRuleParams buildRuleParams = TestBuildRuleParams.create();
      FakeBuildRule buildRule = new FakeBuildRule(BUILD_TARGET, filesystem, buildRuleParams);

      // The BuildContext that will be used by the rule's build() method.
      BuildEngineBuildContext buildContext =
          this.buildContext.withArtifactCache(
              new NoopArtifactCache() {
                @Override
                public ListenableFuture<Void> store(ArtifactInfo info, BorrowablePath output) {
                  try {
                    Thread.sleep(500);
                  } catch (InterruptedException e) {
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                  }
                  return Futures.immediateFuture(null);
                }
              });

      ListeningExecutorService service = listeningDecorator(Executors.newFixedThreadPool(2));

      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory().setExecutorService(service).build()) {
        ListenableFuture<BuildResult> buildResult =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), buildRule)
                .getResult();

        BuildResult result = buildResult.get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());
      }
      assertTrue(service.shutdownNow().isEmpty());

      BuildRuleEvent.Started started =
          TestEventConfigurator.configureTestEvent(
              BuildRuleEvent.ruleKeyCalculationStarted(buildRule, durationTracker));
      assertThat(
          listener.getEvents(),
          Matchers.containsInRelativeOrder(
              started,
              BuildRuleEvent.finished(
                  started,
                  BuildRuleKeys.of(defaultRuleKeyFactory.build(buildRule)),
                  BuildRuleStatus.SUCCESS,
                  CacheResult.miss(),
                  Optional.empty(),
                  Optional.of(BuildRuleSuccessType.BUILT_LOCALLY),
                  false,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty())));
    }

    @Test
    public void testArtifactFetchedFromCache()
        throws InterruptedException, ExecutionException, IOException {
      Step step =
          new AbstractExecutionStep("exploding step") {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              throw new UnsupportedOperationException("build step should not be executed");
            }
          };
      BuildRule buildRule =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              ImmutableList.of(step),
              /* postBuildSteps */ ImmutableList.of(),
              /* pathToOutputFile */ null,
              ImmutableList.of());

      // Simulate successfully fetching the output file from the ArtifactCache.
      ArtifactCache artifactCache = createMock(ArtifactCache.class);
      ImmutableMap<String, String> metadata =
          ImmutableMap.of(
              BuildInfo.MetadataKey.RULE_KEY,
              defaultRuleKeyFactory.build(buildRule).toString(),
              BuildInfo.MetadataKey.BUILD_ID,
              buildContext.getBuildId().toString(),
              BuildInfo.MetadataKey.ORIGIN_BUILD_ID,
              buildContext.getBuildId().toString());
      Path metadataDirectory =
          BuildInfo.getPathToArtifactMetadataDirectory(buildRule.getBuildTarget(), filesystem);
      ImmutableMap<Path, String> desiredZipEntries =
          ImmutableMap.of(
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS),
              ObjectMappers.WRITER.writeValueAsString(ImmutableList.of()),
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATH_HASHES),
              ObjectMappers.WRITER.writeValueAsString(ImmutableMap.of()),
              Paths.get("buck-out/gen/src/com/facebook/orca/orca.jar"),
              "Imagine this is the contents of a valid JAR file.",
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_SIZE),
              "123",
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_HASH),
              HashCode.fromInt(123).toString());
      expect(
              artifactCache.fetchAsync(
                  eq(defaultRuleKeyFactory.build(buildRule)), isA(LazyPath.class)))
          .andDelegateTo(new FakeArtifactCacheThatWritesAZipFile(desiredZipEntries, metadata));

      BuildEngineBuildContext buildContext =
          BuildEngineBuildContext.builder()
              .setBuildContext(FakeBuildContext.withSourcePathResolver(pathResolver))
              .setClock(new DefaultClock())
              .setBuildId(new BuildId())
              .setArtifactCache(artifactCache)
              .build();

      // Build the rule!
      replayAll();

      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        ListenableFuture<BuildResult> buildResult =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), buildRule)
                .getResult();
        buildContext
            .getBuildContext()
            .getEventBus()
            .post(
                CommandEvent.finished(
                    CommandEvent.started("build", ImmutableList.of(), false, 23L),
                    ExitCode.SUCCESS));

        BuildResult result = buildResult.get();
        verifyAll();
        assertTrue(
            "We expect build() to be synchronous in this case, "
                + "so the future should already be resolved.",
            MoreFutures.isSuccess(buildResult));
        assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE, getSuccess(result));
        assertTrue(((BuildableAbstractCachingBuildRule) buildRule).isInitializedFromDisk());
        assertTrue(
            "The entries in the zip should be extracted as a result of building the rule.",
            filesystem.exists(Paths.get("buck-out/gen/src/com/facebook/orca/orca.jar")));
      }
    }

    @Test
    public void testArtifactFetchedFromCacheStillRunsPostBuildSteps()
        throws InterruptedException, ExecutionException, IOException {
      // Add a post build step so we can verify that it's steps are executed.
      Step buildStep = createMock(Step.class);
      expect(buildStep.getDescription(anyObject(ExecutionContext.class)))
          .andReturn("Some Description")
          .anyTimes();
      expect(buildStep.getShortName()).andReturn("Some Short Name").anyTimes();
      expect(buildStep.execute(anyObject(ExecutionContext.class)))
          .andReturn(StepExecutionResults.SUCCESS);

      BuildRule buildRule =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              /* buildSteps */ ImmutableList.of(),
              /* postBuildSteps */ ImmutableList.of(buildStep),
              /* pathToOutputFile */ null,
              ImmutableList.of());

      // Simulate successfully fetching the output file from the ArtifactCache.
      ArtifactCache artifactCache = createMock(ArtifactCache.class);
      ImmutableMap<String, String> metadata =
          ImmutableMap.of(
              BuildInfo.MetadataKey.RULE_KEY,
              defaultRuleKeyFactory.build(buildRule).toString(),
              BuildInfo.MetadataKey.BUILD_ID,
              buildContext.getBuildId().toString(),
              BuildInfo.MetadataKey.ORIGIN_BUILD_ID,
              buildContext.getBuildId().toString());
      Path metadataDirectory =
          BuildInfo.getPathToArtifactMetadataDirectory(buildRule.getBuildTarget(), filesystem);
      ImmutableMap<Path, String> desiredZipEntries =
          ImmutableMap.of(
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS),
              ObjectMappers.WRITER.writeValueAsString(ImmutableList.of()),
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATH_HASHES),
              ObjectMappers.WRITER.writeValueAsString(ImmutableMap.of()),
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_SIZE),
              "123",
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_HASH),
              HashCode.fromInt(123).toString(),
              Paths.get("buck-out/gen/src/com/facebook/orca/orca.jar"),
              "Imagine this is the contents of a valid JAR file.");
      expect(
              artifactCache.fetchAsync(
                  eq(defaultRuleKeyFactory.build(buildRule)), isA(LazyPath.class)))
          .andDelegateTo(new FakeArtifactCacheThatWritesAZipFile(desiredZipEntries, metadata));

      BuildEngineBuildContext buildContext =
          BuildEngineBuildContext.builder()
              .setBuildContext(FakeBuildContext.withSourcePathResolver(pathResolver))
              .setClock(new DefaultClock())
              .setBuildId(new BuildId())
              .setArtifactCache(artifactCache)
              .build();

      // Build the rule!
      replayAll();
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        ListenableFuture<BuildResult> buildResult =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), buildRule)
                .getResult();
        buildContext
            .getBuildContext()
            .getEventBus()
            .post(
                CommandEvent.finished(
                    CommandEvent.started("build", ImmutableList.of(), false, 23L),
                    ExitCode.SUCCESS));

        BuildResult result = buildResult.get();
        verifyAll();
        assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE, result.getSuccess());
        assertTrue(((BuildableAbstractCachingBuildRule) buildRule).isInitializedFromDisk());
        assertTrue(
            "The entries in the zip should be extracted as a result of building the rule.",
            filesystem.exists(Paths.get("buck-out/gen/src/com/facebook/orca/orca.jar")));
      }
    }

    @Test
    public void testMatchingTopLevelRuleKeyAvoidsProcessingDepInShallowMode() throws Exception {
      // Create a dep for the build rule.
      BuildTarget depTarget = BuildTargetFactory.newInstance("//src/com/facebook/orca:lib");
      FakeBuildRule dep = new FakeBuildRule(depTarget);
      FakeBuildRule ruleToTest = new FakeBuildRule(BUILD_TARGET, filesystem, dep);
      RuleKey ruleToTestKey = defaultRuleKeyFactory.build(ruleToTest);

      BuildInfoRecorder recorder = createBuildInfoRecorder(BUILD_TARGET);
      recorder.addBuildMetadata(BuildInfo.MetadataKey.RULE_KEY, ruleToTestKey.toString());
      recorder.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      recorder.writeMetadataToDisk(true);

      // The BuildContext that will be used by the rule's build() method.
      BuildEngineBuildContext context =
          this.buildContext.withArtifactCache(new NoopArtifactCache());

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        // Run the build.
        replayAll();
        BuildResult result =
            cachingBuildEngine
                .build(context, TestExecutionContext.newInstance(), ruleToTest)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, result.getSuccess());
        verifyAll();

        // Verify the events logged to the BuckEventBus.
        List<BuckEvent> events = listener.getEvents();
        assertThat(events, hasItem(BuildRuleEvent.ruleKeyCalculationStarted(dep, durationTracker)));
        BuildRuleEvent.Started started =
            TestEventConfigurator.configureTestEvent(
                BuildRuleEvent.ruleKeyCalculationStarted(ruleToTest, durationTracker));
        assertThat(
            events,
            Matchers.containsInRelativeOrder(
                started,
                BuildRuleEvent.finished(
                    started,
                    BuildRuleKeys.of(ruleToTestKey),
                    BuildRuleStatus.SUCCESS,
                    CacheResult.localKeyUnchangedHit(),
                    Optional.empty(),
                    Optional.of(BuildRuleSuccessType.MATCHING_RULE_KEY),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
      }
    }

    @Test
    public void testMatchingTopLevelRuleKeyStillProcessesDepInDeepMode() throws Exception {
      // Create a dep for the build rule.
      BuildTarget depTarget = BuildTargetFactory.newInstance("//src/com/facebook/orca:lib");
      BuildRuleParams ruleParams = TestBuildRuleParams.create();
      FakeBuildRule dep = new FakeBuildRule(depTarget, filesystem, ruleParams);
      RuleKey depKey = defaultRuleKeyFactory.build(dep);
      BuildInfoRecorder depRecorder = createBuildInfoRecorder(depTarget);
      depRecorder.addBuildMetadata(BuildInfo.MetadataKey.RULE_KEY, depKey.toString());
      depRecorder.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      depRecorder.writeMetadataToDisk(true);

      FakeBuildRule ruleToTest = new FakeBuildRule(BUILD_TARGET, filesystem, dep);
      RuleKey ruleToTestKey = defaultRuleKeyFactory.build(ruleToTest);
      BuildInfoRecorder recorder = createBuildInfoRecorder(BUILD_TARGET);
      recorder.addBuildMetadata(BuildInfo.MetadataKey.RULE_KEY, ruleToTestKey.toString());
      recorder.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      recorder.writeMetadataToDisk(true);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory().setBuildMode(CachingBuildEngine.BuildMode.DEEP).build()) {

        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), ruleToTest)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, result.getSuccess());

        // Verify the events logged to the BuckEventBus.
        List<BuckEvent> events = listener.getEvents();
        BuildRuleEvent.Started startedDep =
            TestEventConfigurator.configureTestEvent(
                BuildRuleEvent.ruleKeyCalculationStarted(dep, durationTracker));
        assertThat(
            events,
            Matchers.containsInRelativeOrder(
                startedDep,
                BuildRuleEvent.finished(
                    startedDep,
                    BuildRuleKeys.of(depKey),
                    BuildRuleStatus.SUCCESS,
                    CacheResult.localKeyUnchangedHit(),
                    Optional.empty(),
                    Optional.of(BuildRuleSuccessType.MATCHING_RULE_KEY),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
        BuildRuleEvent.Started started =
            TestEventConfigurator.configureTestEvent(
                BuildRuleEvent.ruleKeyCalculationStarted(ruleToTest, durationTracker));
        assertThat(
            events,
            Matchers.containsInRelativeOrder(
                started,
                BuildRuleEvent.finished(
                    started,
                    BuildRuleKeys.of(ruleToTestKey),
                    BuildRuleStatus.SUCCESS,
                    CacheResult.localKeyUnchangedHit(),
                    Optional.empty(),
                    Optional.of(BuildRuleSuccessType.MATCHING_RULE_KEY),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
      }
    }

    @Test
    public void testMatchingTopLevelRuleKeyStillProcessesRuntimeDeps() throws Exception {
      // Setup a runtime dependency that is found transitively from the top-level rule.
      BuildTarget buildTarget = BuildTargetFactory.newInstance("//:transitive_dep");
      BuildRuleParams ruleParams = TestBuildRuleParams.create();
      FakeBuildRule transitiveRuntimeDep = new FakeBuildRule(buildTarget, filesystem, ruleParams);
      resolver.addToIndex(transitiveRuntimeDep);
      RuleKey transitiveRuntimeDepKey = defaultRuleKeyFactory.build(transitiveRuntimeDep);

      BuildInfoRecorder recorder = createBuildInfoRecorder(transitiveRuntimeDep.getBuildTarget());
      recorder.addBuildMetadata(BuildInfo.MetadataKey.RULE_KEY, transitiveRuntimeDepKey.toString());
      recorder.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      recorder.writeMetadataToDisk(true);

      // Setup a runtime dependency that is referenced directly by the top-level rule.
      FakeBuildRule runtimeDep =
          new FakeHasRuntimeDeps(
              BuildTargetFactory.newInstance("//:runtime_dep"), filesystem, transitiveRuntimeDep);
      resolver.addToIndex(runtimeDep);
      RuleKey runtimeDepKey = defaultRuleKeyFactory.build(runtimeDep);
      BuildInfoRecorder runtimeDepRec = createBuildInfoRecorder(runtimeDep.getBuildTarget());
      runtimeDepRec.addBuildMetadata(BuildInfo.MetadataKey.RULE_KEY, runtimeDepKey.toString());
      runtimeDepRec.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      runtimeDepRec.writeMetadataToDisk(true);

      // Create a dep for the build rule.
      FakeBuildRule ruleToTest = new FakeHasRuntimeDeps(BUILD_TARGET, filesystem, runtimeDep);
      RuleKey ruleToTestKey = defaultRuleKeyFactory.build(ruleToTest);
      BuildInfoRecorder testRec = createBuildInfoRecorder(BUILD_TARGET);
      testRec.addBuildMetadata(BuildInfo.MetadataKey.RULE_KEY, ruleToTestKey.toString());
      testRec.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      testRec.writeMetadataToDisk(true);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), ruleToTest)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, result.getSuccess());

        // Verify the events logged to the BuckEventBus.
        List<BuckEvent> events = listener.getEvents();
        BuildRuleEvent.Started started =
            TestEventConfigurator.configureTestEvent(
                BuildRuleEvent.ruleKeyCalculationStarted(ruleToTest, durationTracker));
        assertThat(
            events,
            Matchers.containsInRelativeOrder(
                started,
                BuildRuleEvent.finished(
                    started,
                    BuildRuleKeys.of(ruleToTestKey),
                    BuildRuleStatus.SUCCESS,
                    CacheResult.localKeyUnchangedHit(),
                    Optional.empty(),
                    Optional.of(BuildRuleSuccessType.MATCHING_RULE_KEY),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
        BuildRuleEvent.Started startedDep =
            TestEventConfigurator.configureTestEvent(
                BuildRuleEvent.ruleKeyCalculationStarted(runtimeDep, durationTracker));
        assertThat(
            events,
            Matchers.containsInRelativeOrder(
                startedDep,
                BuildRuleEvent.finished(
                    startedDep,
                    BuildRuleKeys.of(runtimeDepKey),
                    BuildRuleStatus.SUCCESS,
                    CacheResult.localKeyUnchangedHit(),
                    Optional.empty(),
                    Optional.of(BuildRuleSuccessType.MATCHING_RULE_KEY),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
        BuildRuleEvent.Started startedTransitive =
            TestEventConfigurator.configureTestEvent(
                BuildRuleEvent.ruleKeyCalculationStarted(transitiveRuntimeDep, durationTracker));
        assertThat(
            events,
            Matchers.containsInRelativeOrder(
                startedTransitive,
                BuildRuleEvent.finished(
                    startedTransitive,
                    BuildRuleKeys.of(transitiveRuntimeDepKey),
                    BuildRuleStatus.SUCCESS,
                    CacheResult.localKeyUnchangedHit(),
                    Optional.empty(),
                    Optional.of(BuildRuleSuccessType.MATCHING_RULE_KEY),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
      }
    }

    @Test
    public void multipleTopLevelRulesDontBlockEachOther() throws Exception {
      Exchanger<Boolean> exchanger = new Exchanger<>();
      Step exchangerStep =
          new AbstractExecutionStep("interleaved_step") {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              try {
                // Forces both rules to wait for the other at this point.
                exchanger.exchange(true, 6, TimeUnit.SECONDS);
              } catch (TimeoutException e) {
                throw new RuntimeException(e);
              }
              return StepExecutionResults.SUCCESS;
            }
          };
      BuildRule interleavedRuleOne =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              /* buildSteps */ ImmutableList.of(exchangerStep),
              /* postBuildSteps */ ImmutableList.of(),
              /* pathToOutputFile */ null,
              ImmutableList.of(InternalFlavor.of("interleaved-1")));
      resolver.addToIndex(interleavedRuleOne);
      BuildRule interleavedRuleTwo =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              /* buildSteps */ ImmutableList.of(exchangerStep),
              /* postBuildSteps */ ImmutableList.of(),
              /* pathToOutputFile */ null,
              ImmutableList.of(InternalFlavor.of("interleaved-2")));
      resolver.addToIndex(interleavedRuleTwo);

      // The engine needs a couple of threads to ensure that it can schedule multiple steps at the
      // same time.
      ListeningExecutorService executorService =
          listeningDecorator(Executors.newFixedThreadPool(4));
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory().setExecutorService(executorService).build()) {
        BuildEngineResult engineResultOne =
            cachingBuildEngine.build(
                buildContext, TestExecutionContext.newInstance(), interleavedRuleOne);
        BuildEngineResult engineResultTwo =
            cachingBuildEngine.build(
                buildContext, TestExecutionContext.newInstance(), interleavedRuleTwo);
        assertThat(engineResultOne.getResult().get().getStatus(), equalTo(BuildRuleStatus.SUCCESS));
        assertThat(engineResultTwo.getResult().get().getStatus(), equalTo(BuildRuleStatus.SUCCESS));
      }
      executorService.shutdown();
    }

    @Test
    public void failedRuntimeDepsArePropagated() throws Exception {
      final String description = "failing step";
      Step failingStep =
          new AbstractExecutionStep(description) {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              return StepExecutionResults.ERROR;
            }
          };
      BuildRule ruleToTest =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              /* buildSteps */ ImmutableList.of(failingStep),
              /* postBuildSteps */ ImmutableList.of(),
              /* pathToOutputFile */ null,
              ImmutableList.of());
      resolver.addToIndex(ruleToTest);

      FakeBuildRule withRuntimeDep =
          new FakeHasRuntimeDeps(
              BuildTargetFactory.newInstance("//:with_runtime_dep"), filesystem, ruleToTest);

      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), withRuntimeDep)
                .getResult()
                .get();

        assertThat(result.getStatus(), equalTo(BuildRuleStatus.CANCELED));
        assertThat(result.getFailure(), instanceOf(BuckUncheckedExecutionException.class));
        Throwable cause = result.getFailure().getCause();
        assertThat(cause, instanceOf(StepFailedException.class));
        assertThat(((StepFailedException) cause).getStep().getShortName(), equalTo(description));
      }
    }

    @Test
    public void pendingWorkIsCancelledOnFailures() throws Exception {
      final String description = "failing step";
      AtomicInteger failedSteps = new AtomicInteger(0);
      Step failingStep =
          new AbstractExecutionStep(description) {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              System.out.println("Failing");
              failedSteps.incrementAndGet();
              return StepExecutionResults.ERROR;
            }
          };
      ImmutableSortedSet.Builder<BuildRule> depsBuilder = ImmutableSortedSet.naturalOrder();
      for (int i = 0; i < 20; i++) {
        BuildRule failingDep =
            createRule(
                filesystem,
                resolver,
                /* deps */ ImmutableSortedSet.of(),
                /* buildSteps */ ImmutableList.of(failingStep),
                /* postBuildSteps */ ImmutableList.of(),
                /* pathToOutputFile */ null,
                ImmutableList.of(InternalFlavor.of("failing-" + i)));
        resolver.addToIndex(failingDep);
        depsBuilder.add(failingDep);
      }

      FakeBuildRule withFailingDeps =
          new FakeBuildRule(
              BuildTargetFactory.newInstance("//:with_failing_deps"), depsBuilder.build());

      // Use a CommandThreadManager to closely match the real-world CachingBuildEngine experience.
      // Limit it to 1 thread so that we don't start multiple deps at the same time.
      try (CommandThreadManager threadManager =
          new CommandThreadManager(
              "cachingBuildEngingTest",
              new ConcurrencyLimit(
                  1,
                  ResourceAllocationFairness.FAIR,
                  1,
                  ResourceAmounts.of(100, 100, 100, 100),
                  ResourceAmounts.of(0, 0, 0, 0)))) {
        CachingBuildEngine cachingBuildEngine =
            cachingBuildEngineFactory()
                .setExecutorService(threadManager.getWeightedListeningExecutorService())
                .build();
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), withFailingDeps)
                .getResult()
                .get();

        assertThat(result.getStatus(), equalTo(BuildRuleStatus.CANCELED));
        assertThat(result.getFailure(), instanceOf(BuckUncheckedExecutionException.class));
        Throwable cause = result.getFailure().getCause();
        assertThat(cause, instanceOf(StepFailedException.class));
        assertThat(failedSteps.get(), equalTo(1));
        assertThat(((StepFailedException) cause).getStep().getShortName(), equalTo(description));
      }
    }

    @Test
    public void failedRuntimeDepsAreNotPropagatedWithKeepGoing() throws Exception {
      buildContext = this.buildContext.withKeepGoing(true);
      final String description = "failing step";
      Step failingStep =
          new AbstractExecutionStep(description) {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              return StepExecutionResults.ERROR;
            }
          };
      BuildRule ruleToTest =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              /* buildSteps */ ImmutableList.of(failingStep),
              /* postBuildSteps */ ImmutableList.of(),
              /* pathToOutputFile */ null,
              ImmutableList.of());
      resolver.addToIndex(ruleToTest);

      FakeBuildRule withRuntimeDep =
          new FakeHasRuntimeDeps(
              BuildTargetFactory.newInstance("//:with_runtime_dep"), filesystem, ruleToTest);

      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), withRuntimeDep)
                .getResult()
                .get();

        assertThat(result.getStatus(), equalTo(BuildRuleStatus.SUCCESS));
      }
    }

    @Test
    public void matchingRuleKeyDoesNotRunPostBuildSteps() throws Exception {
      // Add a post build step so we can verify that it's steps are executed.
      Step failingStep =
          new AbstractExecutionStep("test") {
            @Override
            public StepExecutionResult execute(ExecutionContext context)
                throws IOException, InterruptedException {
              return StepExecutionResults.ERROR;
            }
          };
      BuildRule ruleToTest =
          createRule(
              filesystem,
              resolver,
              /* deps */ ImmutableSortedSet.of(),
              /* buildSteps */ ImmutableList.of(),
              /* postBuildSteps */ ImmutableList.of(failingStep),
              /* pathToOutputFile */ null,
              ImmutableList.of());
      BuildInfoRecorder recorder = createBuildInfoRecorder(ruleToTest.getBuildTarget());

      recorder.addBuildMetadata(
          BuildInfo.MetadataKey.RULE_KEY, defaultRuleKeyFactory.build(ruleToTest).toString());
      recorder.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      recorder.writeMetadataToDisk(true);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), ruleToTest)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, result.getSuccess());
      }
    }

    @Test
    public void testBuildRuleLocallyWithCacheError() throws Exception {
      // Create an artifact cache that always errors out.
      ArtifactCache cache =
          new NoopArtifactCache() {
            @Override
            public ListenableFuture<CacheResult> fetchAsync(RuleKey ruleKey, LazyPath output) {
              return Futures.immediateFuture(
                  CacheResult.error("cache", ArtifactCacheMode.dir, "error"));
            }
          };

      // Use the artifact cache when running a simple rule that will build locally.
      BuildEngineBuildContext buildContext = this.buildContext.withArtifactCache(cache);

      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule = new EmptyBuildRule(target, filesystem);
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertThat(result.getSuccess(), equalTo(BuildRuleSuccessType.BUILT_LOCALLY));
        assertThat(
            result.getCacheResult().map(CacheResult::getType),
            equalTo(Optional.of(CacheResultType.ERROR)));
      }
    }

    @Test
    public void testExceptionMessagesAreInformative() throws Exception {
      AtomicReference<RuntimeException> throwable = new AtomicReference<>();
      BuildTarget buildTarget = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule =
          new AbstractBuildRule(buildTarget, filesystem) {
            @Override
            public SortedSet<BuildRule> getBuildDeps() {
              return ImmutableSortedSet.of();
            }

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              throw throwable.get();
            }

            @Nullable
            @Override
            public SourcePath getSourcePathToOutput() {
              return null;
            }
          };
      throwable.set(new IllegalArgumentException("bad arg"));

      Throwable thrown =
          cachingBuildEngineFactory()
              .build()
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get()
              .getFailure();
      assertThat(thrown, instanceOf(BuckUncheckedExecutionException.class));
      assertThat(thrown.getCause(), new IsInstanceOf(IllegalArgumentException.class));
      assertThat(((ExceptionWithContext) thrown).getContext().get(), containsString("//:rule"));

      // HumanReadableExceptions shouldn't be changed.
      throwable.set(new HumanReadableException("message"));
      thrown =
          cachingBuildEngineFactory()
              .build()
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get()
              .getFailure();
      assertThat(thrown, instanceOf(BuckUncheckedExecutionException.class));
      assertEquals(throwable.get(), thrown.getCause());
      assertThat(((ExceptionWithContext) thrown).getContext().get(), containsString("//:rule"));
    }

    @Test
    public void testDelegateCalledBeforeRuleCreation() throws Exception {
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule = new EmptyBuildRule(target, filesystem);
      final AtomicReference<BuildRule> lastRuleToBeBuilt = new AtomicReference<>();
      CachingBuildEngineDelegate testDelegate =
          new LocalCachingBuildEngineDelegate(fileHashCache) {
            @Override
            public void onRuleAboutToBeBuilt(BuildRule buildRule) {
              super.onRuleAboutToBeBuilt(buildRule);
              lastRuleToBeBuilt.set(buildRule);
            }
          };
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory().setCachingBuildEngineDelegate(testDelegate).build()) {
        cachingBuildEngine
            .build(buildContext, TestExecutionContext.newInstance(), rule)
            .getResult()
            .get();
        assertThat(lastRuleToBeBuilt.get(), is(rule));
      }
    }

    @Test
    public void buildingRuleLocallyInvalidatesOutputs() throws Exception {
      // First, write something to the output file and get it's hash.
      Path output = Paths.get("output/path");
      filesystem.mkdirs(output.getParent());
      filesystem.writeContentsToPath("something", output);
      HashCode originalHashCode = fileHashCache.get(filesystem.resolve(output));

      // Create a simple rule which just writes something new to the output file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule =
          new WriteFile(target, filesystem, "something else", output, /* executable */ false);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());

        // Verify that we have a new hash.
        HashCode newHashCode = fileHashCache.get(filesystem.resolve(output));
        assertThat(newHashCode, Matchers.not(equalTo(originalHashCode)));
      }
    }

    @Test
    public void dependencyFailuresDoesNotOrphanOtherDependencies() throws Exception {
      ListeningExecutorService service = listeningDecorator(Executors.newFixedThreadPool(2));

      // Create a dep chain comprising one side of the dep tree of the main rule, where the first-
      // running rule fails immediately, canceling the second rule, and ophaning at least one rule
      // in the other side of the dep tree.
      BuildTarget target1 = BuildTargetFactory.newInstance("//:dep1");
      BuildRule dep1 =
          new RuleWithSteps(
              target1,
              filesystem,
              TestBuildRuleParams.create(),
              ImmutableList.of(new FailingStep()),
              /* output */ null);
      BuildTarget target2 = BuildTargetFactory.newInstance("//:dep2");
      BuildRule dep2 =
          new RuleWithSteps(
              target2,
              filesystem,
              TestBuildRuleParams.create().withDeclaredDeps(ImmutableSortedSet.of(dep1)),
              ImmutableList.of(new SleepStep(0)),
              /* output */ null);

      // Create another dep chain, which is two deep with rules that just sleep.
      BuildTarget target3 = BuildTargetFactory.newInstance("//:dep3");
      BuildRule dep3 =
          new RuleWithSteps(
              target3,
              filesystem,
              TestBuildRuleParams.create(),
              ImmutableList.of(new SleepStep(300)),
              /* output */ null);
      BuildTarget target5 = BuildTargetFactory.newInstance("//:dep4");
      BuildRule dep4 =
          new RuleWithSteps(
              target5,
              filesystem,
              TestBuildRuleParams.create().withDeclaredDeps(ImmutableSortedSet.of(dep3)),
              ImmutableList.of(new SleepStep(300)),
              /* output */ null);

      // Create the top-level rule which pulls in the two sides of the dep tree.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule =
          new RuleWithSteps(
              target,
              filesystem,
              TestBuildRuleParams.create().withDeclaredDeps(ImmutableSortedSet.of(dep2, dep4)),
              ImmutableList.of(new SleepStep(1000)),
              /* output */ null);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory().setBuildMode(CachingBuildEngine.BuildMode.DEEP).build()) {

        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertTrue(service.shutdownNow().isEmpty());
        assertThat(result.getStatus(), equalTo(BuildRuleStatus.CANCELED));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep1.getBuildTarget()))
                .getStatus(),
            equalTo(BuildRuleStatus.FAIL));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep2.getBuildTarget()))
                .getStatus(),
            equalTo(BuildRuleStatus.CANCELED));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep3.getBuildTarget()))
                .getStatus(),
            Matchers.oneOf(BuildRuleStatus.SUCCESS, BuildRuleStatus.CANCELED));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep4.getBuildTarget()))
                .getStatus(),
            Matchers.oneOf(BuildRuleStatus.SUCCESS, BuildRuleStatus.CANCELED));
      }
    }

    @Test
    public void runningWithKeepGoingBuildsAsMuchAsPossible() throws Exception {
      ListeningExecutorService service = listeningDecorator(Executors.newFixedThreadPool(2));
      buildContext = this.buildContext.withKeepGoing(true);

      // Create a dep chain comprising one side of the dep tree of the main rule, where the first-
      // running rule fails immediately, canceling the second rule, and ophaning at least one rule
      // in the other side of the dep tree.
      BuildTarget target1 = BuildTargetFactory.newInstance("//:dep1");
      BuildRule dep1 =
          new RuleWithSteps(
              target1,
              filesystem,
              TestBuildRuleParams.create(),
              ImmutableList.of(new FailingStep()),
              /* output */ null);
      BuildTarget target2 = BuildTargetFactory.newInstance("//:dep2");
      BuildRule dep2 =
          new RuleWithSteps(
              target2,
              filesystem,
              TestBuildRuleParams.create().withDeclaredDeps(ImmutableSortedSet.of(dep1)),
              ImmutableList.of(new SleepStep(0)),
              /* output */ null);

      // Create another dep chain, which is two deep with rules that just sleep.
      BuildTarget target3 = BuildTargetFactory.newInstance("//:dep3");
      BuildRule dep3 =
          new RuleWithSteps(
              target3,
              filesystem,
              TestBuildRuleParams.create(),
              ImmutableList.of(new SleepStep(300)),
              /* output */ null);
      BuildTarget target4 = BuildTargetFactory.newInstance("//:dep4");
      BuildRule dep4 =
          new RuleWithSteps(
              target4,
              filesystem,
              TestBuildRuleParams.create().withDeclaredDeps(ImmutableSortedSet.of(dep3)),
              ImmutableList.of(new SleepStep(300)),
              /* output */ null);

      // Create the top-level rule which pulls in the two sides of the dep tree.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule =
          new RuleWithSteps(
              target,
              filesystem,
              TestBuildRuleParams.create().withDeclaredDeps(ImmutableSortedSet.of(dep2, dep4)),
              ImmutableList.of(new SleepStep(1000)),
              /* output */ null);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory().setExecutorService(service).build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertThat(result.getStatus(), equalTo(BuildRuleStatus.CANCELED));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep1.getBuildTarget()))
                .getStatus(),
            equalTo(BuildRuleStatus.FAIL));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep2.getBuildTarget()))
                .getStatus(),
            equalTo(BuildRuleStatus.CANCELED));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep3.getBuildTarget()))
                .getStatus(),
            equalTo(BuildRuleStatus.SUCCESS));
        assertThat(
            Preconditions.checkNotNull(cachingBuildEngine.getBuildRuleResult(dep4.getBuildTarget()))
                .getStatus(),
            equalTo(BuildRuleStatus.SUCCESS));
      }
      assertTrue(service.shutdownNow().isEmpty());
    }

    @Test
    public void getNumRulesToBuild() throws Exception {
      BuildRule rule3 =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:rule3"))
              .setOut("out3")
              .build(resolver);
      BuildRule rule2 =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:rule2"))
              .setOut("out2")
              .setSrcs(ImmutableList.of(rule3.getSourcePathToOutput()))
              .build(resolver);
      BuildRule rule1 =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:rule1"))
              .setOut("out1")
              .setSrcs(ImmutableList.of(rule2.getSourcePathToOutput()))
              .build(resolver);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setCachingBuildEngineDelegate(new LocalCachingBuildEngineDelegate(fileHashCache))
              .build()) {
        assertThat(cachingBuildEngine.getNumRulesToBuild(ImmutableList.of(rule1)), equalTo(3));
      }
    }

    @Test
    public void artifactCacheSizeLimit() throws Exception {
      // Create a simple rule which just writes something new to the output file.
      BuildTarget buildTarget = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule =
          new WriteFile(
              buildTarget, filesystem, "data", Paths.get("output/path"), /* executable */ false);

      // Create the build engine with low cache artifact limit which prevents caching the above\
      // rule.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setCachingBuildEngineDelegate(new LocalCachingBuildEngineDelegate(fileHashCache))
              .setArtifactCacheSizeLimit(Optional.of(2L))
              .build()) {
        // Verify that after building successfully, nothing is cached.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertThat(result.getSuccess(), equalTo(BuildRuleSuccessType.BUILT_LOCALLY));
        assertTrue(cache.isEmpty());
      }
    }

    @Test
    public void fetchingFromCacheSeedsFileHashCache() throws Throwable {
      // Create a simple rule which just writes something new to the output file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      Path output = filesystem.getPath("output/path");
      BuildRule rule =
          new WriteFile(target, filesystem, "something else", output, /* executable */ false);

      // Run an initial build to seed the cache.
      try (CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build()) {
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());

        // Clear the file system.
        filesystem.deleteRecursivelyIfExists(Paths.get(""));
        buildInfoStore.deleteMetadata(target);
      }
      // Now run a second build that gets a cache hit.  We use an empty `FakeFileHashCache` which
      // does *not* contain the path, so any attempts to hash it will fail.
      FakeFileHashCache fakeFileHashCache = new FakeFileHashCache(new HashMap<Path, HashCode>());
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setCachingBuildEngineDelegate(new LocalCachingBuildEngineDelegate(fakeFileHashCache))
              .build()) {
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE, result.getSuccess());

        // Verify that the cache hit caused the file hash cache to contain the path.
        assertTrue(fakeFileHashCache.contains(filesystem.resolve(output)));
      }
    }
  }

  public static class InputBasedRuleKeyTests extends CommonFixture {
    public InputBasedRuleKeyTests(CachingBuildEngine.MetadataStorage metadataStorage)
        throws IOException {
      super(metadataStorage);
    }

    @Test
    public void inputBasedRuleKeyAndArtifactAreWrittenForSupportedRules() throws Exception {
      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final RuleKey inputRuleKey = new RuleKey("aaaa");
      final Path output = Paths.get("output");
      final BuildRule rule =
          new InputRuleKeyBuildRule(target, filesystem, params) {
            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      new FakeRuleKeyFactory(ImmutableMap.of(rule.getBuildTarget(), inputRuleKey)),
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());

        // Verify that the artifact was indexed in the cache by the input rule key.
        assertTrue(cache.hasArtifact(inputRuleKey));

        // Verify the input rule key was written to disk.
        OnDiskBuildInfo onDiskBuildInfo =
            buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY),
            equalTo(Optional.of(inputRuleKey)));
      }
    }

    @Test
    public void inputBasedRuleKeyLimit() throws Exception {
      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final RuleKey inputRuleKey = new RuleKey("aaaa");
      final Path output = Paths.get("output");
      final BuildRule rule =
          new InputRuleKeyBuildRule(target, filesystem, params) {
            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              buildableContext.recordArtifact(output);
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "12345", output, /* executable */ false));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      FakeRuleKeyFactory fakeInputRuleKeyFactory =
          new FakeRuleKeyFactory(
              ImmutableMap.of(rule.getBuildTarget(), inputRuleKey),
              ImmutableSet.of(rule.getBuildTarget())) {
            @Override
            public Optional<Long> getInputSizeLimit() {
              return Optional.of(2L);
            }
          };
      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      fakeInputRuleKeyFactory,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());

        // Verify that the artifact was indexed in the cache by the input rule key.
        assertFalse(cache.hasArtifact(inputRuleKey));

        // Verify the input rule key was written to disk.
        OnDiskBuildInfo onDiskBuildInfo =
            buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY),
            equalTo(Optional.empty()));
        assertThat(
            onDiskBuildInfo.getValue(BuildInfo.MetadataKey.OUTPUT_SIZE), equalTo(Optional.of("6")));
        assertThat(
            onDiskBuildInfo.getHash(BuildInfo.MetadataKey.OUTPUT_HASH), equalTo(Optional.empty()));
      }
    }

    @Test
    public void inputBasedRuleKeyLimitCacheHit() throws Exception {
      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final RuleKey ruleKey = new RuleKey("ba5e");
      final RuleKey inputRuleKey = new RuleKey("ba11");
      final Path output = Paths.get("output");
      final BuildRule rule =
          new InputRuleKeyBuildRule(target, filesystem, params) {
            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              buildableContext.recordArtifact(output);
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "12345", output, /* executable */ false));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Prepopulate the cache with an artifact indexed by the input-based rule key.  Pretend
      // OUTPUT_HASH and RECORDED_PATH_HASHES are missing b/c we exceeded the input based
      // threshold.
      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      filesystem.mkdirs(metadataDirectory);
      Path outputPath = pathResolver.getRelativePath(rule.getSourcePathToOutput());
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(outputPath.toString())),
          metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS));

      Path artifact = tmp.newFile("artifact.zip");
      writeEntriesToZip(
          artifact,
          ImmutableMap.of(
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS),
              ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(outputPath.toString())),
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_SIZE),
              "123",
              outputPath,
              "stuff"),
          ImmutableList.of(metadataDirectory));
      cache.store(
          ArtifactInfo.builder()
              .addRuleKeys(ruleKey)
              .putMetadata(BuildInfo.MetadataKey.BUILD_ID, buildContext.getBuildId().toString())
              .putMetadata(
                  BuildInfo.MetadataKey.ORIGIN_BUILD_ID, buildContext.getBuildId().toString())
              .putMetadata(BuildInfo.MetadataKey.RULE_KEY, ruleKey.toString())
              .putMetadata(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY, inputRuleKey.toString())
              .build(),
          BorrowablePath.notBorrowablePath(artifact));

      FakeRuleKeyFactory fakeInputRuleKeyFactory =
          new FakeRuleKeyFactory(
              ImmutableMap.of(rule.getBuildTarget(), inputRuleKey),
              ImmutableSet.of(rule.getBuildTarget())) {
            @Override
            public Optional<Long> getInputSizeLimit() {
              return Optional.of(2L);
            }
          };
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      new FakeRuleKeyFactory(ImmutableMap.of(target, ruleKey)),
                      fakeInputRuleKeyFactory,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE, result.getSuccess());
      }
    }

    @Test
    public void inputBasedRuleKeyMatchAvoidsBuildingLocally() throws Exception {
      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final RuleKey inputRuleKey = new RuleKey("aaaa");
      final BuildRule rule = new FailingInputRuleKeyBuildRule(target, filesystem, params);
      resolver.addToIndex(rule);

      // Create the output file.
      filesystem.writeContentsToPath(
          "stuff", pathResolver.getRelativePath(rule.getSourcePathToOutput()));

      // Prepopulate the recorded paths metadata.
      BuildInfoRecorder recorder = createBuildInfoRecorder(target);
      recorder.addMetadata(
          BuildInfo.MetadataKey.RECORDED_PATHS,
          ImmutableList.of(pathResolver.getRelativePath(rule.getSourcePathToOutput()).toString()));

      // Prepopulate the input rule key on disk, so that we avoid a rebuild.
      recorder.addBuildMetadata(
          BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY, inputRuleKey.toString());
      recorder.writeMetadataToDisk(true);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory,
                      new FakeRuleKeyFactory(ImmutableMap.of(rule.getBuildTarget(), inputRuleKey)),
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.MATCHING_INPUT_BASED_RULE_KEY, result.getSuccess());

        // Verify the input-based and actual rule keys were updated on disk.
        OnDiskBuildInfo onDiskBuildInfo =
            buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.RULE_KEY),
            equalTo(Optional.of(defaultRuleKeyFactory.build(rule))));

        // Verify that the artifact is *not* re-cached under the main rule key.
        LazyPath fetchedArtifact = LazyPath.ofInstance(tmp.newFile("fetched_artifact.zip"));
        assertThat(
            Futures.getUnchecked(
                    cache.fetchAsync(defaultRuleKeyFactory.build(rule), fetchedArtifact))
                .getType(),
            equalTo(CacheResultType.MISS));
      }
    }

    @Test
    public void inputBasedRuleKeyCacheHitAvoidsBuildingLocally() throws Exception {
      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      final RuleKey inputRuleKey = new RuleKey("aaaa");
      BuildRuleParams params = TestBuildRuleParams.create();
      final BuildRule rule = new FailingInputRuleKeyBuildRule(target, filesystem, params);
      resolver.addToIndex(rule);

      // Prepopulate the recorded paths metadata.
      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      filesystem.mkdirs(metadataDirectory);
      Path outputPath = pathResolver.getRelativePath(rule.getSourcePathToOutput());
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(outputPath.toString())),
          metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS));

      // Prepopulate the cache with an artifact indexed by the input-based rule key.
      Path artifact = tmp.newFile("artifact.zip");
      writeEntriesToZip(
          artifact,
          ImmutableMap.of(
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS),
              ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(outputPath.toString())),
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATH_HASHES),
              ObjectMappers.WRITER.writeValueAsString(
                  ImmutableMap.of(outputPath.toString(), HashCode.fromInt(123).toString())),
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_SIZE),
              "123",
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_HASH),
              HashCode.fromInt(123).toString(),
              outputPath,
              "stuff"),
          ImmutableList.of(metadataDirectory));
      cache.store(
          ArtifactInfo.builder()
              .addRuleKeys(inputRuleKey)
              .putMetadata(BuildInfo.MetadataKey.BUILD_ID, buildContext.getBuildId().toString())
              .putMetadata(
                  BuildInfo.MetadataKey.ORIGIN_BUILD_ID, buildContext.getBuildId().toString())
              .putMetadata(BuildInfo.MetadataKey.RULE_KEY, new RuleKey("bbbb").toString())
              .putMetadata(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY, inputRuleKey.toString())
              .build(),
          BorrowablePath.notBorrowablePath(artifact));

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory,
                      new FakeRuleKeyFactory(ImmutableMap.of(rule.getBuildTarget(), inputRuleKey)),
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE_INPUT_BASED, result.getSuccess());

        // Verify the input-based and actual rule keys were updated on disk.
        OnDiskBuildInfo onDiskBuildInfo =
            buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.RULE_KEY),
            equalTo(Optional.of(defaultRuleKeyFactory.build(rule))));
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY),
            equalTo(Optional.of(inputRuleKey)));

        // Verify that the artifact is re-cached correctly under the main rule key.
        Path fetchedArtifact = tmp.newFile("fetched_artifact.zip");
        assertThat(
            Futures.getUnchecked(
                    cache.fetchAsync(
                        defaultRuleKeyFactory.build(rule), LazyPath.ofInstance(fetchedArtifact)))
                .getType(),
            equalTo(CacheResultType.HIT));
        assertEquals(
            Sets.union(
                ImmutableSet.of(metadataDirectory + "/"),
                new ZipInspector(artifact).getZipFileEntries()),
            new ZipInspector(fetchedArtifact).getZipFileEntries());
        new ZipInspector(fetchedArtifact)
            .assertFileContents(
                pathResolver.getRelativePath(rule.getSourcePathToOutput()), "stuff");
      }
    }

    @Test
    public void missingInputBasedRuleKeyDoesNotMatchExistingRuleKey() throws Exception {
      missingInputBasedRuleKeyCausesLocalBuild(Optional.of(new RuleKey("aaaa")));
    }

    @Test
    public void missingInputBasedRuleKeyDoesNotMatchAbsentRuleKey() throws Exception {
      missingInputBasedRuleKeyCausesLocalBuild(Optional.empty());
    }

    private void missingInputBasedRuleKeyCausesLocalBuild(Optional<RuleKey> previousRuleKey)
        throws Exception {
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      final Path output = Paths.get("output");
      BuildRuleParams params = TestBuildRuleParams.create();
      final BuildRule rule =
          new InputRuleKeyBuildRule(target, filesystem, params) {
            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };
      resolver.addToIndex(rule);

      // Create the output file.
      filesystem.writeContentsToPath(
          "stuff", pathResolver.getRelativePath(rule.getSourcePathToOutput()));

      // Prepopulate the recorded paths metadata.
      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      filesystem.mkdirs(metadataDirectory);
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(
              ImmutableList.of(
                  pathResolver.getRelativePath(rule.getSourcePathToOutput()).toString())),
          metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS));

      if (previousRuleKey.isPresent()) {
        // Prepopulate the input rule key on disk.
        filesystem.writeContentsToPath(
            previousRuleKey.get().toString(),
            metadataDirectory.resolve(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY));
      }

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory,
                      new FakeRuleKeyFactory(
                          ImmutableMap.of(), ImmutableSet.of(rule.getBuildTarget())),
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());

        // Verify the input-based and actual rule keys were updated on disk.
        OnDiskBuildInfo onDiskBuildInfo =
            buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.RULE_KEY),
            equalTo(Optional.of(defaultRuleKeyFactory.build(rule))));
        assertThat(
            onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.INPUT_BASED_RULE_KEY),
            equalTo(Optional.empty()));
      }
    }

    private static class FailingInputRuleKeyBuildRule extends InputRuleKeyBuildRule {
      public FailingInputRuleKeyBuildRule(
          BuildTarget buildTarget,
          ProjectFilesystem projectFilesystem,
          BuildRuleParams buildRuleParams) {
        super(buildTarget, projectFilesystem, buildRuleParams);
      }

      @Override
      public ImmutableList<Step> getBuildSteps(
          BuildContext context, BuildableContext buildableContext) {
        return ImmutableList.of(
            new AbstractExecutionStep("false") {
              @Override
              public StepExecutionResult execute(ExecutionContext context)
                  throws IOException, InterruptedException {
                return StepExecutionResults.ERROR;
              }
            });
      }

      @Override
      public SourcePath getSourcePathToOutput() {
        return ExplicitBuildTargetSourcePath.of(getBuildTarget(), Paths.get("output"));
      }
    }
  }

  public static class DepFileTests extends CommonFixture {

    private DefaultDependencyFileRuleKeyFactory depFileFactory;

    public DepFileTests(CachingBuildEngine.MetadataStorage metadataStorage) throws IOException {
      super(metadataStorage);
    }

    @Before
    public void setUpDepFileFixture() {
      depFileFactory =
          new DefaultDependencyFileRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);
    }

    @Test
    public void depFileRuleKeyAndDepFileAreWrittenForSupportedRules() throws Exception {
      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("contents", input);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      final DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(PathSourcePath.of(filesystem, input));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine = engineWithDepFileFactory(depFileFactory);

      // Run the build.
      RuleKey depFileRuleKey =
          depFileFactory
              .build(rule, ImmutableList.of(DependencyFileEntry.of(input, Optional.empty())))
              .getRuleKey();
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, getSuccess(result));

      // Verify the dep file rule key and dep file contents were written to disk.
      OnDiskBuildInfo onDiskBuildInfo =
          buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
      assertThat(
          onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY),
          equalTo(Optional.of(depFileRuleKey)));
      assertThat(
          onDiskBuildInfo.getValues(BuildInfo.MetadataKey.DEP_FILE),
          equalTo(Optional.of(ImmutableList.of(fileToDepFileEntryString(input)))));

      // Verify that the dep file rule key and dep file were written to the cached artifact.
      Path fetchedArtifact = tmp.newFile("fetched_artifact.zip");
      CacheResult cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(
                  defaultRuleKeyFactory.build(rule), LazyPath.ofInstance(fetchedArtifact)));
      assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
      assertThat(
          cacheResult.getMetadata().get(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY),
          equalTo(depFileRuleKey.toString()));
      ZipInspector inspector = new ZipInspector(fetchedArtifact);
      inspector.assertFileContents(
          BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem)
              .resolve(BuildInfo.MetadataKey.DEP_FILE),
          ObjectMappers.WRITER.writeValueAsString(
              ImmutableList.of(fileToDepFileEntryString(input))));
    }

    @Test
    public void depFileRuleKeyMatchAvoidsBuilding() throws Exception {
      // Prepare an input file that should appear in the dep file.
      final Path input = Paths.get("input_file");
      filesystem.touch(input);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final RuleKey depFileRuleKey = new RuleKey("aaaa");
      final Path output = Paths.get("output");
      filesystem.touch(output);
      final BuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new AbstractExecutionStep("false") {
                    @Override
                    public StepExecutionResult execute(ExecutionContext context)
                        throws IOException, InterruptedException {
                      return StepExecutionResults.ERROR;
                    }
                  });
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(PathSourcePath.of(filesystem, input));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine =
          engineWithDepFileFactory(
              new FakeRuleKeyFactory(ImmutableMap.of(rule.getBuildTarget(), depFileRuleKey)));

      // Prepopulate the dep file rule key and dep file.
      BuildInfoRecorder recorder = createBuildInfoRecorder(rule.getBuildTarget());
      recorder.addBuildMetadata(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY, depFileRuleKey.toString());
      recorder.addMetadata(
          BuildInfo.MetadataKey.DEP_FILE, ImmutableList.of(fileToDepFileEntryString(input)));
      // Prepopulate the recorded paths metadata.
      recorder.addMetadata(
          BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of(output.toString()));
      recorder.writeMetadataToDisk(true);

      // Run the build.
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.MATCHING_DEP_FILE_RULE_KEY, result.getSuccess());
    }

    @Test
    public void depFileInputChangeCausesRebuild() throws Exception {
      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(PathSourcePath.of(filesystem, input));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Prepare an input file that should appear in the dep file.
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("something", input);
      RuleKey depFileRuleKey =
          depFileFactory
              .build(rule, ImmutableList.of(DependencyFileEntry.of(input, Optional.empty())))
              .getRuleKey();

      // Prepopulate the dep file rule key and dep file.
      BuildInfoRecorder recorder = createBuildInfoRecorder(rule.getBuildTarget());
      recorder.addBuildMetadata(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY, depFileRuleKey.toString());
      recorder.addMetadata(
          BuildInfo.MetadataKey.DEP_FILE, ImmutableList.of(fileToDepFileEntryString(input)));

      // Prepopulate the recorded paths metadata.
      recorder.addMetadata(
          BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of(output.toString()));
      recorder.writeMetadataToDisk(true);

      // Now modify the input file and invalidate it in the cache.
      filesystem.writeContentsToPath("something else", input);
      fileHashCache.invalidate(filesystem.resolve(input));

      // Run the build.
      CachingBuildEngine cachingBuildEngine = engineWithDepFileFactory(depFileFactory);
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, getSuccess(result));
    }

    @Test
    public void nonDepFileEligibleInputChangeCausesRebuild() throws Exception {
      final Path inputFile = Paths.get("input");

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      final ImmutableSet<SourcePath> inputsBefore = ImmutableSet.of();
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = PathSourcePath.of(filesystem, inputFile);

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return inputsBefore::contains;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of();
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Prepare an input file that will not appear in the dep file. This is to simulate a
      // a dependency that the dep-file generator is not aware of.
      filesystem.writeContentsToPath("something", inputFile);

      // Prepopulate the dep file rule key and dep file.
      RuleKey depFileRuleKey = depFileFactory.build(rule, ImmutableList.of()).getRuleKey();

      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      filesystem.mkdirs(metadataDirectory);
      filesystem.writeContentsToPath(
          depFileRuleKey.toString(),
          metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY));
      final String emptyDepFileContents = "[]";
      filesystem.writeContentsToPath(
          emptyDepFileContents, metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE));

      // Prepopulate the recorded paths metadata.
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(output.toString())),
          metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS));

      // Now modify the input file and invalidate it in the cache.
      filesystem.writeContentsToPath("something else", inputFile);
      fileHashCache.invalidate(filesystem.resolve(inputFile));

      // Run the build.
      CachingBuildEngine cachingBuildEngine = engineWithDepFileFactory(depFileFactory);
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();

      // The dep file should still be empty, yet the target will rebuild because of the change
      // to the non-dep-file-eligible input file
      String newDepFile =
          filesystem.readLines(metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE)).get(0);
      assertEquals(emptyDepFileContents, newDepFile);
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, getSuccess(result));
    }

    @Test
    public void depFileDeletedInputCausesRebuild() throws Exception {
      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of();
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Prepare an input file that should appear in the dep file.
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("something", input);
      RuleKey depFileRuleKey =
          depFileFactory
              .build(rule, ImmutableList.of(DependencyFileEntry.of(input, Optional.empty())))
              .getRuleKey();

      // Prepopulate the dep file rule key and dep file.
      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      filesystem.mkdirs(metadataDirectory);
      filesystem.writeContentsToPath(
          depFileRuleKey.toString(),
          metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY));
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(
              ImmutableList.of(fileToDepFileEntryString(input))),
          metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE));

      // Prepopulate the recorded paths metadata.
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(output.toString())),
          metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS));

      // Now delete the input and invalidate it in the cache.
      filesystem.deleteFileAtPath(input);
      fileHashCache.invalidate(filesystem.resolve(input));

      // Run the build.
      CachingBuildEngine cachingBuildEngine = engineWithDepFileFactory(depFileFactory);
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, getSuccess(result));
    }

    @Test
    public void missingDepFileKeyCausesLocalBuild() throws Exception {
      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));

      // Create a simple rule which just writes a file.
      final BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of();
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      DependencyFileRuleKeyFactory depFileRuleKeyFactory =
          new FakeRuleKeyFactory(ImmutableMap.of(rule.getBuildTarget(), new RuleKey("aa")));

      // Prepare an input file that should appear in the dep file.
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("something", input);

      RuleKey depFileRuleKey =
          depFileFactory
              .build(rule, ImmutableList.of(DependencyFileEntry.of(input, Optional.empty())))
              .getRuleKey();

      // Prepopulate the dep file rule key and dep file.
      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      filesystem.mkdirs(metadataDirectory);
      filesystem.writeContentsToPath(
          depFileRuleKey.toString(),
          metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY));
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(
              ImmutableList.of(fileToDepFileEntryString(input))),
          metadataDirectory.resolve(BuildInfo.MetadataKey.DEP_FILE));

      // Prepopulate the recorded paths metadata.
      filesystem.writeContentsToPath(
          ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(output.toString())),
          metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS));

      // Run the build.
      CachingBuildEngine cachingBuildEngine = engineWithDepFileFactory(depFileRuleKeyFactory);
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, getSuccess(result));

      // Verify the input-based and actual rule keys were updated on disk.
      OnDiskBuildInfo onDiskBuildInfo =
          buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
      assertThat(
          onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.RULE_KEY),
          equalTo(Optional.of(defaultRuleKeyFactory.build(rule))));
      assertThat(
          onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY),
          equalTo(Optional.of(new RuleKey("aa"))));
    }

    public CachingBuildEngine engineWithDepFileFactory(
        DependencyFileRuleKeyFactory depFileFactory) {
      return cachingBuildEngineFactory()
          .setRuleKeyFactories(
              RuleKeyFactories.of(
                  defaultRuleKeyFactory, NOOP_INPUT_BASED_RULE_KEY_FACTORY, depFileFactory))
          .build();
    }
  }

  public static class ManifestTests extends CommonFixture {
    public ManifestTests(CachingBuildEngine.MetadataStorage metadataStorage) throws IOException {
      super(metadataStorage);
    }

    @Test
    public void manifestIsWrittenWhenBuiltLocally() throws Exception {
      DefaultDependencyFileRuleKeyFactory depFilefactory =
          new DefaultDependencyFileRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);

      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("contents", input);

      // Create another input that will be ineligible for the dep file. Such inputs should still
      // be part of the manifest.
      final Path input2 = Paths.get("input2");
      filesystem.writeContentsToPath("contents2", input2);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @AddToRuleKey private final SourcePath otherDep = PathSourcePath.of(filesystem, input2);

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return ImmutableSet.of(path)::contains;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(path);
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setDepFiles(CachingBuildEngine.DepFiles.CACHE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory, inputBasedRuleKeyFactory, depFilefactory))
              .build();

      // Run the build.
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertThat(getSuccess(result), equalTo(BuildRuleSuccessType.BUILT_LOCALLY));

      OnDiskBuildInfo onDiskBuildInfo =
          buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
      RuleKey depFileRuleKey =
          onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY).get();

      // Verify that the manifest written to the cache is correct.
      Path fetchedManifest = tmp.newFile("manifest");
      CacheResult cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(
                  cachingBuildEngine
                      .getManifestRuleKeyForTest(rule, buildContext.getEventBus())
                      .get(),
                  LazyPath.ofInstance(fetchedManifest)));
      assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
      Manifest manifest = loadManifest(fetchedManifest);
      // The manifest should only contain the inputs that were in the dep file. The non-eligible
      // dependency went toward computing the manifest key and thus doesn't need to be in the value.
      assertThat(
          manifest.toMap(),
          equalTo(
              ImmutableMap.of(
                  depFileRuleKey,
                  ImmutableMap.of(
                      input.toString(), fileHashCache.get(filesystem.resolve(input))))));

      // Verify that the artifact is also cached via the dep file rule key.
      Path fetchedArtifact = tmp.newFile("artifact");
      cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(depFileRuleKey, LazyPath.ofInstance(fetchedArtifact)));
      assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
    }

    @Test
    public void manifestIsUpdatedWhenBuiltLocally() throws Exception {
      DefaultDependencyFileRuleKeyFactory depFilefactory =
          new DefaultDependencyFileRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);

      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("contents", input);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(PathSourcePath.of(filesystem, input));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setDepFiles(CachingBuildEngine.DepFiles.CACHE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory, inputBasedRuleKeyFactory, depFilefactory))
              .build();

      // Seed the cache with an existing manifest with a dummy entry.
      Manifest manifest =
          Manifest.fromMap(
              depFilefactory.buildManifestKey(rule).getRuleKey(),
              ImmutableMap.of(
                  new RuleKey("abcd"), ImmutableMap.of("some/path.h", HashCode.fromInt(12))));
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (GZIPOutputStream outputStream = new GZIPOutputStream(byteArrayOutputStream)) {
        manifest.serialize(outputStream);
      }
      cache.store(
          ArtifactInfo.builder()
              .addRuleKeys(
                  cachingBuildEngine
                      .getManifestRuleKeyForTest(rule, buildContext.getEventBus())
                      .get())
              .build(),
          byteArrayOutputStream.toByteArray());

      // Run the build.
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertThat(getSuccess(result), equalTo(BuildRuleSuccessType.BUILT_LOCALLY));

      OnDiskBuildInfo onDiskBuildInfo =
          buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
      RuleKey depFileRuleKey =
          onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY).get();

      // Verify that the manifest written to the cache is correct.
      Path fetchedManifest = tmp.newFile("manifest");
      CacheResult cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(
                  cachingBuildEngine
                      .getManifestRuleKeyForTest(rule, buildContext.getEventBus())
                      .get(),
                  LazyPath.ofInstance(fetchedManifest)));
      assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
      manifest = loadManifest(fetchedManifest);
      assertThat(
          manifest.toMap(),
          equalTo(
              ImmutableMap.of(
                  depFileRuleKey,
                  ImmutableMap.of(input.toString(), fileHashCache.get(filesystem.resolve(input))),
                  new RuleKey("abcd"),
                  ImmutableMap.of("some/path.h", HashCode.fromInt(12)))));

      // Verify that the artifact is also cached via the dep file rule key.
      Path fetchedArtifact = tmp.newFile("artifact");
      cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(depFileRuleKey, LazyPath.ofInstance(fetchedArtifact)));
      assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
    }

    @Test
    public void manifestIsTruncatedWhenGrowingPastSizeLimit() throws Exception {
      DefaultDependencyFileRuleKeyFactory depFilefactory =
          new DefaultDependencyFileRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);

      // Use a genrule to produce the input file.
      final Genrule genrule =
          GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setOut("input")
              .build(resolver, filesystem);
      final Path input =
          pathResolver.getRelativePath(Preconditions.checkNotNull(genrule.getSourcePathToOutput()));
      filesystem.mkdirs(input.getParent());
      filesystem.writeContentsToPath("contents", input);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = genrule.getSourcePathToOutput();

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(PathSourcePath.of(filesystem, input));
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setDepFiles(CachingBuildEngine.DepFiles.CACHE)
              .setMaxDepFileCacheEntries(1L)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory, inputBasedRuleKeyFactory, depFilefactory))
              .build();

      // Seed the cache with an existing manifest with a dummy entry so that it's already at the max
      // size.
      Manifest manifest =
          Manifest.fromMap(
              depFilefactory.buildManifestKey(rule).getRuleKey(),
              ImmutableMap.of(
                  new RuleKey("abcd"), ImmutableMap.of("some/path.h", HashCode.fromInt(12))));
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (GZIPOutputStream outputStream = new GZIPOutputStream(byteArrayOutputStream)) {
        manifest.serialize(outputStream);
      }
      cache.store(
          ArtifactInfo.builder()
              .addRuleKeys(
                  cachingBuildEngine
                      .getManifestRuleKeyForTest(rule, buildContext.getEventBus())
                      .get())
              .build(),
          byteArrayOutputStream.toByteArray());

      // Run the build.
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertThat(getSuccess(result), equalTo(BuildRuleSuccessType.BUILT_LOCALLY));

      OnDiskBuildInfo onDiskBuildInfo =
          buildContext.createOnDiskBuildInfoFor(target, filesystem, buildInfoStore);
      RuleKey depFileRuleKey =
          onDiskBuildInfo.getRuleKey(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY).get();

      // Verify that the manifest is truncated and now only contains the newly written entry.
      Path fetchedManifest = tmp.newFile("manifest");
      CacheResult cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(
                  cachingBuildEngine
                      .getManifestRuleKeyForTest(rule, buildContext.getEventBus())
                      .get(),
                  LazyPath.ofInstance(fetchedManifest)));
      assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
      manifest = loadManifest(fetchedManifest);
      assertThat(
          manifest.toMap(),
          equalTo(
              ImmutableMap.of(
                  depFileRuleKey,
                  ImmutableMap.of(
                      input.toString(), fileHashCache.get(filesystem.resolve(input))))));
    }

    @Test
    public void manifestBasedCacheHit() throws Exception {
      DefaultDependencyFileRuleKeyFactory depFilefactory =
          new DefaultDependencyFileRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final SourcePath input =
          PathSourcePath.of(filesystem, filesystem.getRootPath().getFileSystem().getPath("input"));
      filesystem.touch(pathResolver.getRelativePath(input));
      final Path output = BuildTargets.getGenPath(filesystem, target, "%s/output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = input;

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              buildableContext.recordArtifact(output);
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(input);
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setDepFiles(CachingBuildEngine.DepFiles.CACHE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory, inputBasedRuleKeyFactory, depFilefactory))
              .build();

      // Calculate expected rule keys.
      RuleKey ruleKey = defaultRuleKeyFactory.build(rule);
      RuleKeyAndInputs depFileKey =
          depFilefactory.build(
              rule, ImmutableList.of(DependencyFileEntry.fromSourcePath(input, pathResolver)));

      // Seed the cache with the manifest and a referenced artifact.
      Manifest manifest = new Manifest(depFilefactory.buildManifestKey(rule).getRuleKey());
      manifest.addEntry(
          fileHashCache,
          depFileKey.getRuleKey(),
          pathResolver,
          ImmutableSet.of(input),
          ImmutableSet.of(input));
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (GZIPOutputStream outputStream = new GZIPOutputStream(byteArrayOutputStream)) {
        manifest.serialize(outputStream);
      }
      cache.store(
          ArtifactInfo.builder()
              .addRuleKeys(
                  cachingBuildEngine
                      .getManifestRuleKeyForTest(rule, buildContext.getEventBus())
                      .get())
              .build(),
          byteArrayOutputStream.toByteArray());
      Path artifact = tmp.newFile("artifact.zip");
      Path metadataDirectory = BuildInfo.getPathToArtifactMetadataDirectory(target, filesystem);
      writeEntriesToZip(
          artifact,
          ImmutableMap.of(
              output,
              "stuff",
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATHS),
              ObjectMappers.WRITER.writeValueAsString(ImmutableList.of(output.toString())),
              metadataDirectory.resolve(BuildInfo.MetadataKey.RECORDED_PATH_HASHES),
              ObjectMappers.WRITER.writeValueAsString(
                  ImmutableMap.of(output.toString(), HashCode.fromInt(123).toString())),
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_SIZE),
              "123",
              metadataDirectory.resolve(BuildInfo.MetadataKey.OUTPUT_HASH),
              HashCode.fromInt(123).toString()),
          ImmutableList.of());
      cache.store(
          ArtifactInfo.builder()
              .addRuleKeys(depFileKey.getRuleKey())
              .putMetadata(BuildInfo.MetadataKey.BUILD_ID, buildContext.getBuildId().toString())
              .putMetadata(
                  BuildInfo.MetadataKey.ORIGIN_BUILD_ID, buildContext.getBuildId().toString())
              .putMetadata(
                  BuildInfo.MetadataKey.DEP_FILE_RULE_KEY, depFileKey.getRuleKey().toString())
              .putMetadata(
                  BuildInfo.MetadataKey.DEP_FILE,
                  ObjectMappers.WRITER.writeValueAsString(
                      depFileKey
                          .getInputs()
                          .stream()
                          .map(pathResolver::getRelativePath)
                          .collect(ImmutableList.toImmutableList())))
              .build(),
          BorrowablePath.notBorrowablePath(artifact));

      // Run the build.
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertThat(
          getSuccess(result), equalTo(BuildRuleSuccessType.FETCHED_FROM_CACHE_MANIFEST_BASED));

      // Verify that the result has been re-written to the cache with the expected meta-data.
      for (RuleKey key : ImmutableSet.of(ruleKey, depFileKey.getRuleKey())) {
        LazyPath fetchedArtifact = LazyPath.ofInstance(tmp.newFile("fetched_artifact.zip"));
        CacheResult cacheResult = Futures.getUnchecked(cache.fetchAsync(key, fetchedArtifact));
        assertThat(cacheResult.getType(), equalTo(CacheResultType.HIT));
        assertThat(
            cacheResult.getMetadata().get(BuildInfo.MetadataKey.RULE_KEY),
            equalTo(ruleKey.toString()));
        assertThat(
            cacheResult.getMetadata().get(BuildInfo.MetadataKey.DEP_FILE_RULE_KEY),
            equalTo(depFileKey.getRuleKey().toString()));
        assertThat(
            cacheResult.getMetadata().get(BuildInfo.MetadataKey.DEP_FILE),
            equalTo(
                ObjectMappers.WRITER.writeValueAsString(
                    depFileKey
                        .getInputs()
                        .stream()
                        .map(pathResolver::getRelativePath)
                        .collect(ImmutableList.toImmutableList()))));
        Files.delete(fetchedArtifact.get());
      }
    }

    @Test
    public void staleExistingManifestIsIgnored() throws Exception {
      DefaultDependencyFileRuleKeyFactory depFilefactory =
          new DefaultDependencyFileRuleKeyFactory(
              FIELD_LOADER, fileHashCache, pathResolver, ruleFinder);

      // Create a simple rule which just writes a file.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      final SourcePath input =
          PathSourcePath.of(filesystem, filesystem.getRootPath().getFileSystem().getPath("input"));
      filesystem.touch(pathResolver.getRelativePath(input));
      final Path output = Paths.get("output");
      DepFileBuildRule rule =
          new DepFileBuildRule(target, filesystem, params) {
            @AddToRuleKey private final SourcePath path = input;

            @Override
            public ImmutableList<Step> getBuildSteps(
                BuildContext context, BuildableContext buildableContext) {
              return ImmutableList.of(
                  new WriteFileStep(filesystem, "", output, /* executable */ false));
            }

            @Override
            public Predicate<SourcePath> getCoveredByDepFilePredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> true;
            }

            @Override
            public Predicate<SourcePath> getExistenceOfInterestPredicate(
                SourcePathResolver pathResolver) {
              return (SourcePath path) -> false;
            }

            @Override
            public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
                BuildContext context, CellPathResolver cellPathResolver) {
              return ImmutableList.of(input);
            }

            @Override
            public SourcePath getSourcePathToOutput() {
              return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
            }
          };

      // Create the build engine.
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setDepFiles(CachingBuildEngine.DepFiles.CACHE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      defaultRuleKeyFactory, inputBasedRuleKeyFactory, depFilefactory))
              .build();

      // Write out a stale manifest to the disk.
      RuleKey staleDepFileRuleKey = new RuleKey("dead");
      Manifest manifest = new Manifest(new RuleKey("beef"));
      manifest.addEntry(
          fileHashCache,
          staleDepFileRuleKey,
          pathResolver,
          ImmutableSet.of(input),
          ImmutableSet.of(input));
      Path manifestPath = ManifestRuleKeyManager.getManifestPath(rule);
      filesystem.mkdirs(manifestPath.getParent());
      try (OutputStream outputStream = filesystem.newFileOutputStream(manifestPath)) {
        manifest.serialize(outputStream);
      }

      // Run the build.
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertThat(getSuccess(result), equalTo(BuildRuleSuccessType.BUILT_LOCALLY));

      // Verify there's no stale entry in the manifest.
      LazyPath fetchedManifest = LazyPath.ofInstance(tmp.newFile("fetched_artifact.zip"));
      CacheResult cacheResult =
          Futures.getUnchecked(
              cache.fetchAsync(
                  depFilefactory.buildManifestKey(rule).getRuleKey(), fetchedManifest));
      assertTrue(cacheResult.getType().isSuccess());
      Manifest cachedManifest = loadManifest(fetchedManifest.get());
      assertThat(cachedManifest.toMap().keySet(), Matchers.not(hasItem(staleDepFileRuleKey)));
    }
  }

  public static class UncachableRuleTests extends CommonFixture {
    public UncachableRuleTests(CachingBuildEngine.MetadataStorage metadataStorage)
        throws IOException {
      super(metadataStorage);
    }

    @Test
    public void uncachableRulesDoNotTouchTheCache() throws Exception {
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRuleParams params = TestBuildRuleParams.create();
      BuildRule rule =
          new UncachableRule(target, filesystem, params, ImmutableList.of(), Paths.get("foo.out"));
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      NOOP_INPUT_BASED_RULE_KEY_FACTORY,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build();
      BuildResult result =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());
      assertThat(
          "Should not attempt to fetch from cache",
          result.getCacheResult().map(CacheResult::getType),
          equalTo(Optional.of(CacheResultType.IGNORED)));
      assertEquals("should not have written to the cache", 0, cache.getArtifactCount());
    }

    private static class UncachableRule extends RuleWithSteps
        implements SupportsDependencyFileRuleKey {
      public UncachableRule(
          BuildTarget buildTarget,
          ProjectFilesystem projectFilesystem,
          BuildRuleParams buildRuleParams,
          ImmutableList<Step> steps,
          Path output) {
        super(buildTarget, projectFilesystem, buildRuleParams, steps, output);
      }

      @Override
      public boolean useDependencyFileRuleKeys() {
        return true;
      }

      @Override
      public Predicate<SourcePath> getCoveredByDepFilePredicate(SourcePathResolver pathResolver) {
        return (SourcePath path) -> true;
      }

      @Override
      public Predicate<SourcePath> getExistenceOfInterestPredicate(
          SourcePathResolver pathResolver) {
        return (SourcePath path) -> false;
      }

      @Override
      public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
          BuildContext context, CellPathResolver cellPathResolver) throws IOException {
        return ImmutableList.of();
      }

      @Override
      public boolean isCacheable() {
        return false;
      }
    }
  }

  public static class ScheduleOverrideTests extends CommonFixture {
    public ScheduleOverrideTests(CachingBuildEngine.MetadataStorage metadataStorage)
        throws IOException {
      super(metadataStorage);
    }

    @Test
    public void customWeights() throws Exception {
      BuildTarget target1 = BuildTargetFactory.newInstance("//:rule1");
      ControlledRule rule1 =
          new ControlledRule(
              target1, filesystem, RuleScheduleInfo.builder().setJobsMultiplier(2).build());
      BuildTarget target2 = BuildTargetFactory.newInstance("//:rule2");
      ControlledRule rule2 =
          new ControlledRule(
              target2, filesystem, RuleScheduleInfo.builder().setJobsMultiplier(2).build());
      ListeningMultiSemaphore semaphore =
          new ListeningMultiSemaphore(
              ResourceAmounts.of(3, 0, 0, 0), ResourceAllocationFairness.FAIR);
      CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setExecutorService(
                  new WeightedListeningExecutorService(
                      semaphore,
                      /* defaultWeight */ ResourceAmounts.of(1, 0, 0, 0),
                      listeningDecorator(Executors.newCachedThreadPool())))
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      NOOP_INPUT_BASED_RULE_KEY_FACTORY,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build();
      ListenableFuture<BuildResult> result1 =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule1)
              .getResult();
      rule1.waitForStart();
      assertThat(rule1.hasStarted(), equalTo(true));
      ListenableFuture<BuildResult> result2 =
          cachingBuildEngine
              .build(buildContext, TestExecutionContext.newInstance(), rule2)
              .getResult();
      Thread.sleep(250);
      assertThat(semaphore.getQueueLength(), equalTo(1));
      assertThat(rule2.hasStarted(), equalTo(false));
      rule1.finish();
      result1.get();
      rule2.finish();
      result2.get();
    }

    private class ControlledRule extends AbstractBuildRule implements OverrideScheduleRule {

      private final RuleScheduleInfo ruleScheduleInfo;

      private final Semaphore started = new Semaphore(0);
      private final Semaphore finish = new Semaphore(0);

      private ControlledRule(
          BuildTarget buildTarget,
          ProjectFilesystem projectFilesystem,
          RuleScheduleInfo ruleScheduleInfo) {
        super(buildTarget, projectFilesystem);
        this.ruleScheduleInfo = ruleScheduleInfo;
      }

      @Override
      public SortedSet<BuildRule> getBuildDeps() {
        return ImmutableSortedSet.of();
      }

      @Override
      public ImmutableList<Step> getBuildSteps(
          BuildContext context, BuildableContext buildableContext) {
        return ImmutableList.of(
            new AbstractExecutionStep("step") {
              @Override
              public StepExecutionResult execute(ExecutionContext context)
                  throws IOException, InterruptedException {
                started.release();
                finish.acquire();
                return StepExecutionResults.SUCCESS;
              }
            });
      }

      @Nullable
      @Override
      public SourcePath getSourcePathToOutput() {
        return null;
      }

      @Override
      public RuleScheduleInfo getRuleScheduleInfo() {
        return ruleScheduleInfo;
      }

      public void finish() {
        finish.release();
      }

      public void waitForStart() {
        started.acquireUninterruptibly();
        started.release();
      }

      public boolean hasStarted() {
        return started.availablePermits() == 1;
      }
    }
  }

  public static class BuildRuleEventTests extends CommonFixture {

    // Use a executor service which uses a new thread for every task to help expose case where
    // the build engine issues begin and end rule events on different threads.
    private static final ListeningExecutorService SERVICE = new NewThreadExecutorService();

    public BuildRuleEventTests(CachingBuildEngine.MetadataStorage metadataStorage)
        throws IOException {
      super(metadataStorage);
    }

    @Ignore
    @Test
    public void eventsForBuiltLocallyRuleAreOnCorrectThreads() throws Exception {
      // Create a noop simple rule.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule = new EmptyBuildRule(target, filesystem);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setExecutorService(SERVICE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      NOOP_INPUT_BASED_RULE_KEY_FACTORY,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());
      }
      // Verify that events have correct thread IDs
      assertRelatedBuildRuleEventsOnSameThread(
          FluentIterable.from(listener.getEvents()).filter(BuildRuleEvent.class));
      assertRelatedBuildRuleEventsDuration(
          FluentIterable.from(listener.getEvents()).filter(BuildRuleEvent.class));
    }

    @Test
    public void eventsForMatchingRuleKeyRuleAreOnCorrectThreads() throws Exception {
      // Create a simple rule and set it up so that it has a matching rule key.
      BuildTarget buildTarget = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule = new EmptyBuildRule(buildTarget, filesystem);
      BuildInfoRecorder recorder = createBuildInfoRecorder(rule.getBuildTarget());
      recorder.addBuildMetadata(
          BuildInfo.MetadataKey.RULE_KEY, defaultRuleKeyFactory.build(rule).toString());
      recorder.addMetadata(BuildInfo.MetadataKey.RECORDED_PATHS, ImmutableList.of());
      recorder.writeMetadataToDisk(true);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setExecutorService(SERVICE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      NOOP_INPUT_BASED_RULE_KEY_FACTORY,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, result.getSuccess());
      }

      // Verify that events have correct thread IDs
      assertRelatedBuildRuleEventsOnSameThread(
          FluentIterable.from(listener.getEvents()).filter(BuildRuleEvent.class));
      assertRelatedBuildRuleEventsDuration(
          FluentIterable.from(listener.getEvents()).filter(BuildRuleEvent.class));
    }

    @Ignore
    @Test
    public void eventsForBuiltLocallyRuleAndDepAreOnCorrectThreads() throws Exception {
      // Create a simple rule and dep.
      BuildTarget depTarget = BuildTargetFactory.newInstance("//:dep");
      BuildRule dep = new EmptyBuildRule(depTarget, filesystem);
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      BuildRule rule = new EmptyBuildRule(target, filesystem, dep);

      // Create the build engine.
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory()
              .setExecutorService(SERVICE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      NOOP_RULE_KEY_FACTORY,
                      NOOP_INPUT_BASED_RULE_KEY_FACTORY,
                      NOOP_DEP_FILE_RULE_KEY_FACTORY))
              .build()) {
        // Run the build.
        BuildResult result =
            cachingBuildEngine
                .build(buildContext, TestExecutionContext.newInstance(), rule)
                .getResult()
                .get();
        assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());
      }
      // Verify that events have correct thread IDs
      assertRelatedBuildRuleEventsOnSameThread(
          FluentIterable.from(listener.getEvents()).filter(BuildRuleEvent.class));
      assertRelatedBuildRuleEventsDuration(
          FluentIterable.from(listener.getEvents()).filter(BuildRuleEvent.class));
    }

    @Test
    public void originForBuiltLocally() throws Exception {

      // Create a noop simple rule.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      Path output = filesystem.getPath("output/path");
      BuildRule rule =
          new WriteFile(target, filesystem, "something else", output, /* executable */ false);

      // Run the build and extract the event.
      CachingBuildEngine cachingBuildEngine = cachingBuildEngineFactory().build();
      BuildId buildId = new BuildId("id");
      BuildResult result =
          cachingBuildEngine
              .build(buildContext.withBuildId(buildId), TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result.getSuccess());
      BuildRuleEvent.Finished event =
          RichStream.from(listener.getEvents())
              .filter(BuildRuleEvent.Finished.class)
              .filter(e -> e.getBuildRule().equals(rule))
              .findAny()
              .orElseThrow(AssertionError::new);

      // Verify we found the correct build id.
      assertThat(event.getOrigin(), equalTo(Optional.of(buildId)));
    }

    @Test
    public void originForMatchingRuleKey() throws Exception {

      // Create a noop simple rule.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      Path output = filesystem.getPath("output/path");
      BuildRule rule =
          new WriteFile(target, filesystem, "something else", output, /* executable */ false);

      // Run an initial build to seed the cache.
      CachingBuildEngine cachingBuildEngine1 = cachingBuildEngineFactory().build();
      BuildId buildId1 = new BuildId("id1");
      BuildResult result1 =
          cachingBuildEngine1
              .build(buildContext.withBuildId(buildId1), TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result1.getSuccess());

      // Run the build and extract the event.
      CachingBuildEngine cachingBuildEngine2 = cachingBuildEngineFactory().build();
      BuildId buildId2 = new BuildId("id2");
      BuildResult result2 =
          cachingBuildEngine2
              .build(buildContext.withBuildId(buildId2), TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, getSuccess(result2));
      BuildRuleEvent.Finished event =
          RichStream.from(listener.getEvents())
              .filter(BuildRuleEvent.Finished.class)
              .filter(e -> e.getBuildRule().equals(rule))
              .findAny()
              .orElseThrow(AssertionError::new);

      // Verify we found the correct build id.
      assertThat(event.getOrigin(), equalTo(Optional.of(buildId1)));
    }

    @Test
    public void originForCached() throws Exception {

      // Create a noop simple rule.
      BuildTarget target = BuildTargetFactory.newInstance("//:rule");
      Path output = filesystem.getPath("output/path");
      BuildRule rule =
          new WriteFile(target, filesystem, "something else", output, /* executable */ false);

      // Run an initial build to seed the cache.
      CachingBuildEngine cachingBuildEngine1 = cachingBuildEngineFactory().build();
      BuildId buildId1 = new BuildId("id1");
      BuildResult result1 =
          cachingBuildEngine1
              .build(buildContext.withBuildId(buildId1), TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, result1.getSuccess());

      filesystem.deleteRecursivelyIfExists(Paths.get(""));
      buildInfoStore.deleteMetadata(target);

      // Run the build and extract the event.
      CachingBuildEngine cachingBuildEngine2 = cachingBuildEngineFactory().build();
      BuildId buildId2 = new BuildId("id2");
      BuildResult result2 =
          cachingBuildEngine2
              .build(buildContext.withBuildId(buildId2), TestExecutionContext.newInstance(), rule)
              .getResult()
              .get();
      assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE, getSuccess(result2));
      BuildRuleEvent.Finished event =
          RichStream.from(listener.getEvents())
              .filter(BuildRuleEvent.Finished.class)
              .filter(e -> e.getBuildRule().equals(rule))
              .findAny()
              .orElseThrow(AssertionError::new);

      // Verify we found the correct build id.
      assertThat(event.getOrigin(), equalTo(Optional.of(buildId1)));
    }

    /** Verify that the begin and end events in build rule event pairs occur on the same thread. */
    private void assertRelatedBuildRuleEventsOnSameThread(Iterable<BuildRuleEvent> events) {
      Map<Long, List<BuildRuleEvent>> grouped = new HashMap<>();
      for (BuildRuleEvent event : events) {
        if (!grouped.containsKey(event.getThreadId())) {
          grouped.put(event.getThreadId(), new ArrayList<>());
        }
        grouped.get(event.getThreadId()).add(event);
      }
      for (List<BuildRuleEvent> queue : grouped.values()) {
        queue.sort(Comparator.comparingLong(BuildRuleEvent::getNanoTime));
        ImmutableList<String> queueDescription =
            queue
                .stream()
                .map(event -> String.format("%s@%s", event, event.getNanoTime()))
                .collect(ImmutableList.toImmutableList());
        Iterator<BuildRuleEvent> itr = queue.iterator();

        while (itr.hasNext()) {
          BuildRuleEvent event1 = itr.next();
          BuildRuleEvent event2 = itr.next();

          assertThat(
              String.format(
                  "Two consecutive events (%s,%s) should have the same BuildTarget. (%s)",
                  event1, event2, queueDescription),
              event1.getBuildRule().getBuildTarget(),
              equalTo(event2.getBuildRule().getBuildTarget()));
          assertThat(
              String.format(
                  "Two consecutive events (%s,%s) should be suspend/resume or resume/suspend. (%s)",
                  event1, event2, queueDescription),
              event1.isRuleRunningAfterThisEvent(),
              equalTo(!event2.isRuleRunningAfterThisEvent()));
        }
      }
    }

    private void assertRelatedBuildRuleEventsDuration(Iterable<BuildRuleEvent> events) {
      Map<BuildRule, List<BuildRuleEvent>> grouped = new HashMap<>();
      for (BuildRuleEvent event : events) {
        if (!grouped.containsKey(event.getBuildRule())) {
          grouped.put(event.getBuildRule(), new ArrayList<>());
        }
        grouped.get(event.getBuildRule()).add(event);
      }
      for (List<BuildRuleEvent> queue : grouped.values()) {
        queue.sort(Comparator.comparingLong(BuildRuleEvent::getNanoTime));
        long count = 0, wallStart = 0, nanoStart = 0, wall = 0, nano = 0, thread = 0;
        for (BuildRuleEvent event : queue) {
          if (event instanceof BuildRuleEvent.BeginningBuildRuleEvent) {
            if (count++ == 0) {
              wallStart = event.getTimestamp();
              nanoStart = event.getNanoTime();
            }
            assertEquals(
                wall + event.getTimestamp() - wallStart,
                event.getDuration().getWallMillisDuration());
            assertEquals(
                nano + event.getNanoTime() - nanoStart, event.getDuration().getNanoDuration());
            assertEquals(thread, event.getDuration().getThreadUserNanoDuration());
          } else if (event instanceof BuildRuleEvent.EndingBuildRuleEvent) {
            BuildRuleEvent.BeginningBuildRuleEvent beginning =
                ((BuildRuleEvent.EndingBuildRuleEvent) event).getBeginningEvent();
            thread += event.getThreadUserNanoTime() - beginning.getThreadUserNanoTime();
            assertEquals(
                wall + event.getTimestamp() - wallStart,
                event.getDuration().getWallMillisDuration());
            assertEquals(
                nano + event.getNanoTime() - nanoStart, event.getDuration().getNanoDuration());
            assertEquals(thread, event.getDuration().getThreadUserNanoDuration());
            if (--count == 0) {
              wall += event.getTimestamp() - wallStart;
              nano += event.getNanoTime() - nanoStart;
            }
          }
        }
        assertEquals("Different number of beginning and ending events: " + queue, 0, count);
      }
    }

    /** A {@link ListeningExecutorService} which runs every task on a completely new thread. */
    private static class NewThreadExecutorService extends AbstractListeningExecutorService {

      @Override
      public void shutdown() {}

      @Nonnull
      @Override
      public List<Runnable> shutdownNow() {
        return ImmutableList.of();
      }

      @Override
      public boolean isShutdown() {
        return false;
      }

      @Override
      public boolean isTerminated() {
        return false;
      }

      @Override
      public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit)
          throws InterruptedException {
        return false;
      }

      /** Spawn a new thread for every command. */
      @Override
      public void execute(@Nonnull Runnable command) {
        new Thread(command).start();
      }
    }
  }

  public static class InitializableFromDiskTests extends CommonFixture {
    private static final String DEPFILE_INPUT_CONTENT = "depfile input";
    private static final String NON_DEPFILE_INPUT_CONTENT = "depfile input";
    private PathSourcePath depfileInput;
    private PathSourcePath nonDepfileInput;
    private NoopBuildRuleWithValueAddedToRuleKey dependency;
    private AbstractCachingBuildRuleWithInputs buildRule;
    private ArtifactCache artifactCache;
    private BuildRuleSuccessType lastSuccessType;

    private static class NoopBuildRuleWithValueAddedToRuleKey extends NoopBuildRule {
      @AddToRuleKey private int value = 0;

      public NoopBuildRuleWithValueAddedToRuleKey(
          BuildTarget buildTarget, ProjectFilesystem projectFilesystem) {
        super(buildTarget, projectFilesystem);
      }

      @Override
      public SortedSet<BuildRule> getBuildDeps() {
        return ImmutableSortedSet.of();
      }
    }

    public InitializableFromDiskTests(CachingBuildEngine.MetadataStorage storageType)
        throws IOException {
      super(storageType);
    }

    @Test
    public void runTestForAllSuccessTypes() throws Exception {
      // This is done to ensure that every success type is covered by a test.
      // TODO(cjhopman): add test for failed case.
      for (BuildRuleSuccessType successType : EnumSet.allOf(BuildRuleSuccessType.class)) {
        reset();
        switch (successType) {
          case BUILT_LOCALLY:
            testBuiltLocally();
            break;
          case FETCHED_FROM_CACHE:
            testFetchedFromCache();
            break;
          case MATCHING_RULE_KEY:
            testMatchingRuleKey();
            break;
          case FETCHED_FROM_CACHE_INPUT_BASED:
            testFetchedFromCacheInputBased();
            break;
          case FETCHED_FROM_CACHE_MANIFEST_BASED:
            testFetchedFromCacheManifestBased();
            break;
          case MATCHING_INPUT_BASED_RULE_KEY:
            testMatchingInputBasedKey();
            break;
          case MATCHING_DEP_FILE_RULE_KEY:
            testMatchingDepFileKey();
            break;
            // Every success type should be covered by a test. Don't add a default clause here.
        }
        assertEquals(lastSuccessType, successType);
      }
    }

    @Test
    public void testMatchingRuleKey() throws Exception {
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertEquals(BuildRuleSuccessType.MATCHING_RULE_KEY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertEquals(
          "Matching rule key should not invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test
    public void testMatchingInputBasedKey() throws Exception {
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      dependency.value = 1;
      assertEquals(BuildRuleSuccessType.MATCHING_INPUT_BASED_RULE_KEY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertEquals(
          "Matching input based rule key should not invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test
    public void testMatchingDepFileKey() throws Exception {
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeNonDepfileInput("something else");
      assertEquals(BuildRuleSuccessType.MATCHING_DEP_FILE_RULE_KEY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertEquals(
          "Matching rule key should not invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test
    public void testFetchedFromCache() throws Exception {
      // write to cache
      String newContent = "new content";
      writeDepfileInput(newContent);
      // populate cache
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      doClean();

      writeDepfileInput(DEPFILE_INPUT_CONTENT);
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput(newContent);
      assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Fetching from cache should invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test
    public void testFetchedFromCacheInputBased() throws Exception {
      // write to cache
      String newContent = "new content";
      writeDepfileInput(newContent);
      // populate cache
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      doClean();

      writeDepfileInput(DEPFILE_INPUT_CONTENT);
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      dependency.value = 1;
      writeDepfileInput(newContent);
      assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE_INPUT_BASED, doBuild().getSuccess());

      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Fetching from cache should invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test
    public void testFetchedFromCacheManifestBased() throws Exception {
      // write to cache
      String newContent = "new content";
      writeDepfileInput(newContent);
      // populate cache
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      doClean();

      writeDepfileInput(DEPFILE_INPUT_CONTENT);
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput(newContent);
      writeNonDepfileInput(newContent);
      assertEquals(BuildRuleSuccessType.FETCHED_FROM_CACHE_MANIFEST_BASED, doBuild().getSuccess());

      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Fetching from cache should invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test
    public void testBuiltLocally() throws Exception {
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput("new content");
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Building locally should invalidate InitializableFromDisk state.",
          firstState,
          secondState);
    }

    @Test(timeout = 10000)
    public void testBuildLocallyWithImmediateRemoteSynchronization() throws Exception {
      RemoteBuildRuleSynchronizer synchronizer = new RemoteBuildRuleSynchronizer();
      synchronizer.switchToAlwaysWaitingMode();

      // Signal completion of the build rule before the caching build engine requests it.
      // waitForBuildRuleToFinishRemotely call inside caching build engine should result in an
      // ImmediateFuture with the completion handler executed on the caching build engine's thread.
      synchronizer.signalCompletionOfBuildRule(BUILD_TARGET.getFullyQualifiedName());

      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild(synchronizer).getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput("new content");
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Building locally should invalidate InitializableFromDisk state.",
          firstState,
          secondState);

      // Check that the build engine waited for the remote build of rule to finish.
      assertTrue(
          synchronizer.buildCompletionWaitingFutureCreatedForTarget(
              BUILD_TARGET.getFullyQualifiedName()));
    }

    @Test(timeout = 10000)
    public void testBuildLocallyWithDelayedRemoteSynchronization() throws Exception {
      RemoteBuildRuleSynchronizer synchronizer = new RemoteBuildRuleSynchronizer();
      synchronizer.switchToAlwaysWaitingMode();

      // Signal the completion of the build rule asynchronously.
      // waitForBuildRuleToFinishRemotely call inside caching build engine should result in an
      // Future that later has its completion handler invoked by the Thread below.
      Thread signalBuildRuleCompletedThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException e) {
                  fail("Test was interrupted");
                }
                synchronizer.signalCompletionOfBuildRule(BUILD_TARGET.getFullyQualifiedName());
              });
      signalBuildRuleCompletedThread.start();

      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild(synchronizer).getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput("new content");
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Building locally should invalidate InitializableFromDisk state.",
          firstState,
          secondState);

      signalBuildRuleCompletedThread.join(1000);

      // Check that the build engine waited for the remote build of rule to finish.
      assertTrue(
          synchronizer.buildCompletionWaitingFutureCreatedForTarget(
              BUILD_TARGET.getFullyQualifiedName()));
    }

    @Test(timeout = 10000)
    public void testBuildLocallyWhenRemoteBuildNotStartedAndAlwaysWaitSetToFalse()
        throws Exception {
      RemoteBuildRuleSynchronizer synchronizer = new RemoteBuildRuleSynchronizer();

      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild(synchronizer).getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput("new content");
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Building locally should invalidate InitializableFromDisk state.",
          firstState,
          secondState);

      // Check that the build engine did not wait for the remote build of rule to finish
      assertFalse(
          synchronizer.buildCompletionWaitingFutureCreatedForTarget(
              BUILD_TARGET.getFullyQualifiedName()));
    }

    @Test(timeout = 10000)
    public void testBuildLocallyWhenRemoteBuildStartedAndAlwaysWaitSetToFalse() throws Exception {
      RemoteBuildRuleSynchronizer synchronizer = new RemoteBuildRuleSynchronizer();

      // Signal that the build has started, which should ensure build waits.
      synchronizer.signalStartedRemoteBuildingOfBuildRule(BUILD_TARGET.getFullyQualifiedName());

      // Signal the completion of the build rule asynchronously.
      // waitForBuildRuleToFinishRemotely call inside caching build engine should result in an
      // Future that later has its completion handler invoked by the Thread below.
      Thread signalBuildRuleCompletedThread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException e) {
                  fail("Test was interrupted");
                }
                synchronizer.signalCompletionOfBuildRule(BUILD_TARGET.getFullyQualifiedName());
              });
      signalBuildRuleCompletedThread.start();

      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild(synchronizer).getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object firstState = buildRule.getBuildOutputInitializer().getBuildOutput();

      writeDepfileInput("new content");
      assertEquals(BuildRuleSuccessType.BUILT_LOCALLY, doBuild().getSuccess());
      assertTrue(buildRule.isInitializedFromDisk());
      Object secondState = buildRule.getBuildOutputInitializer().getBuildOutput();

      assertNotEquals(
          "Building locally should invalidate InitializableFromDisk state.",
          firstState,
          secondState);

      signalBuildRuleCompletedThread.join(1000);

      // Check that the build engine waited for the remote build of rule to finish.
      assertTrue(
          synchronizer.buildCompletionWaitingFutureCreatedForTarget(
              BUILD_TARGET.getFullyQualifiedName()));
    }

    private BuildEngineBuildContext createBuildContext(BuildId buildId) {
      return BuildEngineBuildContext.builder()
          .setBuildContext(FakeBuildContext.withSourcePathResolver(pathResolver))
          .setClock(new DefaultClock())
          .setBuildId(buildId)
          .setArtifactCache(artifactCache)
          .build();
    }

    private void writeDepfileInput(String content) throws IOException {
      filesystem.mkdirs(depfileInput.getRelativePath().getParent());
      filesystem.writeContentsToPath(content, depfileInput.getRelativePath());
    }

    private void writeNonDepfileInput(String content) throws IOException {
      filesystem.mkdirs(nonDepfileInput.getRelativePath().getParent());
      filesystem.writeContentsToPath(content, nonDepfileInput.getRelativePath());
    }

    @Before
    public void setUpChild() throws Exception {
      depfileInput = FakeSourcePath.of(filesystem, "path/in/depfile");
      nonDepfileInput = FakeSourcePath.of(filesystem, "path/not/in/depfile");
      dependency =
          new NoopBuildRuleWithValueAddedToRuleKey(
              BUILD_TARGET.withFlavors(InternalFlavor.of("noop")), filesystem);
      buildRule =
          createInputBasedRule(
              filesystem,
              resolver,
              ImmutableSortedSet.of(dependency),
              ImmutableList.of(),
              ImmutableList.of(),
              null,
              ImmutableList.of(),
              ImmutableSortedSet.of(depfileInput, nonDepfileInput),
              ImmutableSortedSet.of(depfileInput));
      reset();
    }

    public void reset() throws IOException {
      writeDepfileInput(DEPFILE_INPUT_CONTENT);
      writeNonDepfileInput(NON_DEPFILE_INPUT_CONTENT);
      dependency.value = 0;
      artifactCache = new InMemoryArtifactCache();
      lastSuccessType = null;
      doClean();
    }

    private BuildResult doBuild() throws Exception {
      return doBuild(defaultRemoteBuildRuleCompletionWaiter);
    }

    private BuildResult doBuild(RemoteBuildRuleCompletionWaiter synchronizer) throws Exception {
      fileHashCache.invalidateAll();
      try (CachingBuildEngine cachingBuildEngine =
          cachingBuildEngineFactory(synchronizer)
              .setDepFiles(CachingBuildEngine.DepFiles.CACHE)
              .setRuleKeyFactories(
                  RuleKeyFactories.of(
                      new DefaultRuleKeyFactory(
                          FIELD_LOADER, fileHashCache, pathResolver, ruleFinder),
                      new TestInputBasedRuleKeyFactory(
                          FIELD_LOADER,
                          fileHashCache,
                          pathResolver,
                          ruleFinder,
                          NO_INPUT_FILE_SIZE_LIMIT),
                      new DefaultDependencyFileRuleKeyFactory(
                          FIELD_LOADER, fileHashCache, pathResolver, ruleFinder)))
              .build()) {
        BuildResult result =
            cachingBuildEngine
                .build(
                    createBuildContext(new BuildId()),
                    TestExecutionContext.newInstance(),
                    buildRule)
                .getResult()
                .get();
        if (DEBUG && !result.isSuccess()) {
          result.getFailure().printStackTrace();
        }
        lastSuccessType = result.getSuccess();
        return result;
      }
    }

    private void doClean() throws IOException {
      buildInfoStoreManager.close();
      buildInfoStoreManager = new BuildInfoStoreManager();
      filesystem.deleteRecursivelyIfExists(Paths.get("buck-out"));
      Files.createDirectories(filesystem.resolve(filesystem.getBuckPaths().getScratchDir()));
      buildInfoStore = buildInfoStoreManager.get(filesystem, metadataStorage);
      System.out.println(
          buildInfoStore.readMetadata(dependency.getBuildTarget(), BuildInfo.MetadataKey.RULE_KEY));
    }
  }


  // TODO(mbolin): Test that when the success files match, nothing is built and nothing is
  // written back to the cache.

  // TODO(mbolin): Test that when the value in the success file does not agree with the current
  // value, the rule is rebuilt and the result is written back to the cache.

  // TODO(mbolin): Test that a failure when executing the build steps is propagated
  // appropriately.

  // TODO(mbolin): Test what happens when the cache's methods throw an exception.

  private static BuildRule createRule(
      ProjectFilesystem filesystem,
      BuildRuleResolver ruleResolver,
      ImmutableSortedSet<BuildRule> deps,
      List<Step> buildSteps,
      ImmutableList<Step> postBuildSteps,
      @Nullable String pathToOutputFile,
      ImmutableList<Flavor> flavors) {

    BuildTarget buildTarget = BUILD_TARGET.withFlavors(flavors);

    BuildableAbstractCachingBuildRule rule =
        new BuildableAbstractCachingBuildRule(
            buildTarget, filesystem, deps, pathToOutputFile, buildSteps, postBuildSteps);
    ruleResolver.addToIndex(rule);
    return rule;
  }

  private static AbstractCachingBuildRuleWithInputs createInputBasedRule(
      ProjectFilesystem filesystem,
      BuildRuleResolver ruleResolver,
      ImmutableSortedSet<BuildRule> deps,
      List<Step> buildSteps,
      ImmutableList<Step> postBuildSteps,
      @Nullable String pathToOutputFile,
      ImmutableList<Flavor> flavors,
      ImmutableSortedSet<SourcePath> inputs,
      ImmutableSortedSet<SourcePath> depfileInputs) {
    BuildTarget buildTarget = BUILD_TARGET.withFlavors(flavors);
    AbstractCachingBuildRuleWithInputs rule =
        new AbstractCachingBuildRuleWithInputs(
            buildTarget,
            filesystem,
            pathToOutputFile,
            buildSteps,
            postBuildSteps,
            deps,
            inputs,
            depfileInputs);
    ruleResolver.addToIndex(rule);
    return rule;
  }

  private static Manifest loadManifest(Path path) throws IOException {
    try (InputStream inputStream =
        new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
      return new Manifest(inputStream);
    }
  }

  private static BuildRuleSuccessType getSuccess(BuildResult result) {
    switch (result.getStatus()) {
      case FAIL:
        Throwables.throwIfUnchecked(Preconditions.checkNotNull(result.getFailure()));
        throw new RuntimeException(result.getFailure());
      case CANCELED:
        throw new RuntimeException("result is canceled");
      case SUCCESS:
        return result.getSuccess();
      default:
        throw new IllegalStateException();
    }
  }

  private static String fileToDepFileEntryString(Path file) {
    DependencyFileEntry entry = DependencyFileEntry.of(file, Optional.empty());

    try {
      return ObjectMappers.WRITER.writeValueAsString(entry);
    } catch (JsonProcessingException e) {
      throw new AssertionError(e);
    }
  }

  private static class BuildableAbstractCachingBuildRule extends AbstractBuildRule
      implements HasPostBuildSteps, InitializableFromDisk<Object> {

    private final ImmutableSortedSet<BuildRule> deps;
    private final Path pathToOutputFile;
    private final List<Step> buildSteps;
    private final ImmutableList<Step> postBuildSteps;
    private final BuildOutputInitializer<Object> buildOutputInitializer;

    private BuildableAbstractCachingBuildRule(
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        ImmutableSortedSet<BuildRule> deps,
        @Nullable String pathToOutputFile,
        List<Step> buildSteps,
        ImmutableList<Step> postBuildSteps) {
      super(buildTarget, projectFilesystem);
      this.deps = deps;
      this.pathToOutputFile = pathToOutputFile == null ? null : Paths.get(pathToOutputFile);
      this.buildSteps = buildSteps;
      this.postBuildSteps = postBuildSteps;
      this.buildOutputInitializer = new BuildOutputInitializer<>(buildTarget, this);
    }

    @Override
    @Nullable
    public SourcePath getSourcePathToOutput() {
      if (pathToOutputFile == null) {
        return null;
      }
      return ExplicitBuildTargetSourcePath.of(getBuildTarget(), pathToOutputFile);
    }

    @Override
    public SortedSet<BuildRule> getBuildDeps() {
      return deps;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext context, BuildableContext buildableContext) {
      if (pathToOutputFile != null) {
        buildableContext.recordArtifact(pathToOutputFile);
      }
      return ImmutableList.copyOf(buildSteps);
    }

    @Override
    public ImmutableList<Step> getPostBuildSteps(BuildContext context) {
      return postBuildSteps;
    }

    @Override
    public Object initializeFromDisk() {
      return new Object();
    }

    @Override
    public BuildOutputInitializer<Object> getBuildOutputInitializer() {
      return buildOutputInitializer;
    }

    public boolean isInitializedFromDisk() {
      return getBuildOutputInitializer().getBuildOutput() != null;
    }
  }

  private static class AbstractCachingBuildRuleWithInputs extends BuildableAbstractCachingBuildRule
      implements SupportsInputBasedRuleKey, SupportsDependencyFileRuleKey {
    @AddToRuleKey private final ImmutableSortedSet<SourcePath> inputs;
    private final ImmutableSortedSet<SourcePath> depfileInputs;

    private AbstractCachingBuildRuleWithInputs(
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        @Nullable String pathToOutputFile,
        List<Step> buildSteps,
        ImmutableList<Step> postBuildSteps,
        ImmutableSortedSet<BuildRule> deps,
        ImmutableSortedSet<SourcePath> inputs,
        ImmutableSortedSet<SourcePath> depfileInputs) {
      super(buildTarget, projectFilesystem, deps, pathToOutputFile, buildSteps, postBuildSteps);
      this.inputs = inputs;
      this.depfileInputs = depfileInputs;
    }

    @Override
    public boolean useDependencyFileRuleKeys() {
      return true;
    }

    @Override
    public Predicate<SourcePath> getCoveredByDepFilePredicate(SourcePathResolver pathResolver) {
      return path -> true;
    }

    @Override
    public Predicate<SourcePath> getExistenceOfInterestPredicate(SourcePathResolver pathResolver) {
      return path -> false;
    }

    @Override
    public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
        BuildContext context, CellPathResolver cellPathResolver) throws IOException {
      return ImmutableList.copyOf(depfileInputs);
    }
  }

  /**
   * Implementation of {@link ArtifactCache} that, when its fetch method is called, takes the
   * location of requested {@link File} and writes a zip file there with the entries specified to
   * its constructor.
   *
   * <p>This makes it possible to react to a call to {@link ArtifactCache#store(ArtifactInfo,
   * BorrowablePath)} and ensure that there will be a zip file in place immediately after the
   * captured method has been invoked.
   */
  private static class FakeArtifactCacheThatWritesAZipFile implements ArtifactCache {

    private final ImmutableMap<Path, String> desiredEntries;
    private final ImmutableMap<String, String> metadata;

    public FakeArtifactCacheThatWritesAZipFile(
        ImmutableMap<Path, String> desiredEntries, ImmutableMap<String, String> metadata) {
      this.desiredEntries = desiredEntries;
      this.metadata = metadata;
    }

    @Override
    public ListenableFuture<CacheResult> fetchAsync(RuleKey ruleKey, LazyPath output) {
      try {
        writeEntriesToZip(output.get(), ImmutableMap.copyOf(desiredEntries), ImmutableList.of());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return Futures.immediateFuture(
          CacheResult.hit("dir", ArtifactCacheMode.dir).withMetadata(metadata));
    }

    @Override
    public void skipPendingAndFutureAsyncFetches() {
      // Async requests are not supported by FakeArtifactCacheThatWritesAZipFile, so do nothing
    }

    @Override
    public ListenableFuture<Void> store(ArtifactInfo info, BorrowablePath output) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<ImmutableMap<RuleKey, CacheResult>> multiContainsAsync(
        ImmutableSet<RuleKey> ruleKeys) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<CacheDeleteResult> deleteAsync(List<RuleKey> ruleKeys) {
      throw new RuntimeException("Delete operation is not supported");
    }

    @Override
    public CacheReadMode getCacheReadMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      throw new UnsupportedOperationException();
    }
  }

  private static class FakeHasRuntimeDeps extends FakeBuildRule implements HasRuntimeDeps {

    private final ImmutableSortedSet<BuildRule> runtimeDeps;

    public FakeHasRuntimeDeps(
        BuildTarget target, ProjectFilesystem filesystem, BuildRule... runtimeDeps) {
      super(target, filesystem);
      this.runtimeDeps = ImmutableSortedSet.copyOf(runtimeDeps);
    }

    @Override
    public Stream<BuildTarget> getRuntimeDeps(SourcePathRuleFinder ruleFinder) {
      return runtimeDeps.stream().map(BuildRule::getBuildTarget);
    }
  }

  private abstract static class InputRuleKeyBuildRule
      extends AbstractBuildRuleWithDeclaredAndExtraDeps implements SupportsInputBasedRuleKey {
    public InputRuleKeyBuildRule(
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        BuildRuleParams buildRuleParams) {
      super(buildTarget, projectFilesystem, buildRuleParams);
    }
  }

  private abstract static class DepFileBuildRule extends AbstractBuildRuleWithDeclaredAndExtraDeps
      implements SupportsDependencyFileRuleKey {
    public DepFileBuildRule(
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        BuildRuleParams buildRuleParams) {
      super(buildTarget, projectFilesystem, buildRuleParams);
    }

    @Override
    public boolean useDependencyFileRuleKeys() {
      return true;
    }
  }

  private static class RuleWithSteps extends AbstractBuildRuleWithDeclaredAndExtraDeps {

    private final ImmutableList<Step> steps;
    @Nullable private final Path output;

    public RuleWithSteps(
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        BuildRuleParams buildRuleParams,
        ImmutableList<Step> steps,
        @Nullable Path output) {
      super(buildTarget, projectFilesystem, buildRuleParams);
      this.steps = steps;
      this.output = output;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext context, BuildableContext buildableContext) {
      return steps;
    }

    @Nullable
    @Override
    public SourcePath getSourcePathToOutput() {
      if (output == null) {
        return null;
      }
      return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
    }
  }

  private static class SleepStep extends AbstractExecutionStep {

    private final long millis;

    public SleepStep(long millis) {
      super(String.format("sleep %sms", millis));
      this.millis = millis;
    }

    @Override
    public StepExecutionResult execute(ExecutionContext context)
        throws IOException, InterruptedException {
      Thread.sleep(millis);
      return StepExecutionResults.SUCCESS;
    }
  }

  private static class FailingStep extends AbstractExecutionStep {

    public FailingStep() {
      super("failing step");
    }

    @Override
    public StepExecutionResult execute(ExecutionContext context)
        throws IOException, InterruptedException {
      return StepExecutionResults.ERROR;
    }
  }

  private static void writeEntriesToZip(
      Path file, ImmutableMap<Path, String> entries, ImmutableList<Path> directories)
      throws IOException {
    try (CustomZipOutputStream zip = ZipOutputStreams.newOutputStream(file)) {
      for (Map.Entry<Path, String> mapEntry : entries.entrySet()) {
        CustomZipEntry entry = new CustomZipEntry(mapEntry.getKey());
        // We want deterministic ZIPs, so avoid mtimes. -1 is timzeone independent, 0 is not.
        entry.setTime(ZipConstants.getFakeTime());
        // We set the external attributes to this magic value which seems to match the attributes
        // of entries created by {@link InMemoryArtifactCache}.
        entry.setExternalAttributes(33188L << 16);
        zip.putNextEntry(entry);
        zip.write(mapEntry.getValue().getBytes());
        zip.closeEntry();
      }
      for (Path dir : directories) {
        CustomZipEntry entry = new CustomZipEntry(dir.toString() + "/");
        // We want deterministic ZIPs, so avoid mtimes. -1 is timzeone independent, 0 is not.
        entry.setTime(ZipConstants.getFakeTime());
        // We set the external attributes to this magic value which seems to match the attributes
        // of entries created by {@link InMemoryArtifactCache}.
        entry.setExternalAttributes(33188L << 16);
        zip.putNextEntry(entry);
        zip.closeEntry();
      }
    }
  }

  private static class EmptyBuildRule extends AbstractBuildRule {

    private final ImmutableSortedSet<BuildRule> deps;

    public EmptyBuildRule(
        BuildTarget buildTarget, ProjectFilesystem projectFilesystem, BuildRule... deps) {
      super(buildTarget, projectFilesystem);
      this.deps = ImmutableSortedSet.copyOf(deps);
    }

    @Override
    public SortedSet<BuildRule> getBuildDeps() {
      return deps;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext context, BuildableContext buildableContext) {
      return ImmutableList.of();
    }

    @Nullable
    @Override
    public SourcePath getSourcePathToOutput() {
      return null;
    }
  }
}
