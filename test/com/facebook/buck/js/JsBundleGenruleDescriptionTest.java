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

package com.facebook.buck.js;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.android.packageable.AndroidPackageableCollector;
import com.facebook.buck.apple.AppleBundleResources;
import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.sandbox.NoSandboxExecutionStrategy;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.toolchain.impl.ToolchainProviderBuilder;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import org.easymock.EasyMock;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JsBundleGenruleDescriptionTest {
  private static final BuildTarget genruleTarget =
      BuildTargetFactory.newInstance("//:bundle-genrule");
  private static final BuildTarget defaultBundleTarget =
      BuildTargetFactory.newInstance("//js:bundle");
  private TestSetup setup;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    setUp(new Flavor[0]);
  }

  private void setUp(Flavor... extraFlavors) {
    setUp(defaultBundleTarget, extraFlavors);
  }

  private void setUp(BuildTarget bundleTarget, Flavor... extraFlavors) {
    setUpWithOptions(builderOptions(bundleTarget), extraFlavors);
  }

  private void setUpWithRewriteSourceMap(Flavor... extraFlavors) {
    setUpWithOptions(builderOptions().rewriteSourcemap(), extraFlavors);
  }

  private void setUpWithRewriteMiscDir(Flavor... extraFlavors) {
    setUpWithOptions(builderOptions().rewriteMisc(), extraFlavors);
  }

  private void setupWithSkipResources(Flavor... extraFlavors) {
    setUpWithOptions(builderOptions().skipResources(), extraFlavors);
  }

  private void setUpWithOptions(JsBundleGenruleBuilder.Options options, Flavor... extraFlavors) {
    JsTestScenario scenario =
        JsTestScenario.builder().bundleWithDeps(options.jsBundle).bundleGenrule(options).build();

    setup =
        new TestSetup(scenario, genruleTarget.withAppendedFlavors(extraFlavors), options.jsBundle);
  }

  @Test
  public void dependsOnSpecifiedJsBundle() {
    assertThat(setup.genrule().getBuildDeps(), hasItem(setup.jsBundle()));
  }

  @Test
  public void forwardsFlavorsToJsBundle() {
    Flavor[] extraFlavors = {JsFlavors.IOS, JsFlavors.RELEASE};
    setUp(defaultBundleTarget.withAppendedFlavors(JsFlavors.RAM_BUNDLE_INDEXED), extraFlavors);
    assertEquals(
        ImmutableSortedSet.of(setup.jsBundle(extraFlavors)), setup.genrule().getBuildDeps());
  }

  @Test
  public void failsForNonJsBundleTargets() {
    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        Matchers.equalTo(
            "The 'js_bundle' argument of //:bundle-genrule, //js:bundle, must correspond to a js_bundle() rule."));
    JsTestScenario scenario = JsTestScenario.builder().arbitraryRule(defaultBundleTarget).build();

    new JsBundleGenruleBuilder(genruleTarget, defaultBundleTarget, scenario.filesystem)
        .build(scenario.resolver, scenario.filesystem);
  }

  @Test
  public void underlyingJsBundleIsARuntimeDep() {
    assertArrayEquals(
        new BuildTarget[] {defaultBundleTarget},
        setup.genrule().getRuntimeDeps(new SourcePathRuleFinder(setup.resolver())).toArray());
  }

  @Test
  public void hasSameBundleNameAsJsBundle() {
    assertEquals(setup.jsBundle().getBundleName(), setup.genrule().getBundleName());
  }

  @Test
  public void addsBundleAndBundleNameAsEnvironmentVariable() {
    SourcePathResolver pathResolver = sourcePathResolver();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, builder);
    ImmutableMap<String, String> env = builder.build();

    assertThat(
        env,
        hasEntry(
            "JS_DIR",
            pathResolver.getAbsolutePath(setup.jsBundle().getSourcePathToOutput()).toString()));
    assertThat(env, hasEntry("JS_BUNDLE_NAME", setup.jsBundle().getBundleName()));
  }

  @Test
  public void exposesReleaseFlavorAsEnvironmentVariable() {
    setUp(JsFlavors.RELEASE);
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(setup.resolver()));
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, env);
    assertThat(env.build(), hasEntry("RELEASE", "1"));
  }

  @Test
  public void withoutReleaseFlavorEnvVariableIsEmpty() {
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(setup.resolver()));
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, env);
    assertThat(env.build(), hasEntry("RELEASE", ""));
  }

  @Test
  public void exposesAndroidFlavorAsEnvironmentVariable() {
    setUp(JsFlavors.ANDROID);
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(setup.resolver()));
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, env);
    assertThat(env.build(), hasEntry("PLATFORM", "android"));
  }

  @Test
  public void exposesIosFlavorAsEnvironmentVariable() {
    setUp(JsFlavors.IOS);
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(setup.resolver()));
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, env);
    assertThat(env.build(), hasEntry("PLATFORM", "ios"));
  }

  @Test
  public void withoutPlatformFlavorEnvVariableIsEmpty() {
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(setup.resolver()));
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, env);
    assertThat(env.build(), hasEntry("PLATFORM", ""));
  }

  @Test
  public void addsResourcesDirectoryAsEnvironmentVariable() {
    setUp();

    SourcePathResolver pathResolver = sourcePathResolver();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, builder);

    assertThat(
        builder.build(),
        hasEntry(
            "RES_DIR",
            pathResolver.getAbsolutePath(setup.genrule().getSourcePathToResources()).toString()));
  }

  @Test
  public void addsMiscDirectoryAsEnvironmentVariable() {
    setUp();

    SourcePathResolver pathResolver = sourcePathResolver();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, builder);

    assertThat(
        builder.build(),
        hasEntry(
            "MISC_DIR",
            pathResolver.getAbsolutePath(setup.genrule().getSourcePathToMisc()).toString()));
  }

  @Test
  public void exportsResourcesOfJsBundle() {
    assertEquals(
        setup.jsBundle().getSourcePathToResources(), setup.genrule().getSourcePathToResources());
  }

  @Test
  public void delegatesAndroidPackageableBehaviorToBundle() {
    setUp(defaultBundleTarget.withAppendedFlavors(JsFlavors.ANDROID));

    JsBundleAndroid jsBundleAndroid = setup.jsBundleAndroid();
    assertEquals(
        jsBundleAndroid.getRequiredPackageables(), setup.genrule().getRequiredPackageables());

    AndroidPackageableCollector collector = packageableCollectorMock(setup);
    setup.genrule().addToCollector(collector);
    verify(collector);
  }

  @Test
  public void doesNotExposePackageablesWithSkipResources() {
    setupWithSkipResources(JsFlavors.ANDROID);

    assertEquals(ImmutableList.of(), setup.genrule().getRequiredPackageables());
    AndroidPackageableCollector collector = packageableCollectorMock(setup);
    setup.genrule().addToCollector(collector);
    verify(collector);
  }

  @Test
  public void returnsNothingIfUnderlyingBundleIsNotForAndroid() {
    assertEquals(ImmutableList.of(), setup.genrule().getRequiredPackageables());
  }

  @Test
  public void addAppleBundleResourcesIsDelegatedToUnderlyingBundle() {
    AppleBundleResources.Builder genruleBuilder = AppleBundleResources.builder();
    new JsBundleGenruleDescription(
            new ToolchainProviderBuilder().build(), new NoSandboxExecutionStrategy())
        .addAppleBundleResources(
            genruleBuilder,
            setup.targetNode(),
            setup.rule().getProjectFilesystem(),
            setup.resolver());

    AppleBundleResources expected =
        AppleBundleResources.builder()
            .addDirsContainingResourceDirs(
                setup.genrule().getSourcePathToOutput(),
                setup.jsBundle().getSourcePathToResources())
            .build();
    assertEquals(expected, genruleBuilder.build());
  }

  @Test
  public void addAppleBundleResourcesExposesNothingWithSkipResources() {
    setupWithSkipResources();

    AppleBundleResources.Builder genruleBuilder = AppleBundleResources.builder();
    new JsBundleGenruleDescription(
            new ToolchainProviderBuilder().build(), new NoSandboxExecutionStrategy())
        .addAppleBundleResources(
            genruleBuilder,
            setup.targetNode(),
            setup.rule().getProjectFilesystem(),
            setup.resolver());

    AppleBundleResources expected = AppleBundleResources.builder().build();
    assertEquals(expected, genruleBuilder.build());
  }

  @Test
  public void exportsSourceMapOfJsBundle() {
    assertEquals(
        setup.jsBundle().getSourcePathToSourceMap(), setup.genrule().getSourcePathToSourceMap());
  }

  @Test
  public void exportsMiscOfJsBundle() {
    assertEquals(setup.jsBundle().getSourcePathToMisc(), setup.genrule().getSourcePathToMisc());
  }

  @Test
  public void exposesSourceMapOfJsBundleWithSpecialFlavor() {
    setUp(JsFlavors.SOURCE_MAP);

    DefaultSourcePathResolver pathResolver = sourcePathResolver();

    assertEquals(
        pathResolver.getRelativePath(setup.jsBundle().getSourcePathToSourceMap()),
        pathResolver.getRelativePath(setup.rule().getSourcePathToOutput()));
  }

  @Test
  public void exposesMiscOfJsBundleWithSpecialFlavor() {
    setUp(JsFlavors.MISC);

    DefaultSourcePathResolver pathResolver = sourcePathResolver();

    assertEquals(
        pathResolver.getRelativePath(setup.jsBundle().getSourcePathToMisc()),
        pathResolver.getRelativePath(setup.rule().getSourcePathToOutput()));
  }

  @Test
  public void exposeDepsFileOfJsBundleWithSpecialFlavor() {
    setUp(JsFlavors.DEPENDENCY_FILE);
    DefaultSourcePathResolver pathResolver = sourcePathResolver();

    assertEquals(
        pathResolver.getRelativePath(setup.jsBundleDepsFile().getSourcePathToOutput()),
        pathResolver.getRelativePath(setup.rule().getSourcePathToOutput()));
  }

  @Test
  public void createsJsDir() {
    JsBundleGenrule genrule = setup.genrule();
    BuildContext context = FakeBuildContext.withSourcePathResolver(sourcePathResolver());
    FakeBuildableContext buildableContext = new FakeBuildableContext();
    ImmutableList<Step> buildSteps =
        ImmutableList.copyOf(genrule.getBuildSteps(context, buildableContext));

    MkdirStep expectedStep =
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(),
                genrule.getProjectFilesystem(),
                context.getSourcePathResolver().getRelativePath(genrule.getSourcePathToOutput())));
    assertThat(buildSteps, hasItem(expectedStep));

    int mkJsDirIdx = buildSteps.indexOf(expectedStep);
    assertThat(buildSteps.subList(mkJsDirIdx, buildSteps.size()), not(hasItem(any(RmStep.class))));
  }

  @Test
  public void dependsOnTargetsInMacros() {
    BuildTarget locationTarget = BuildTargetFactory.newInstance("//location:target");

    JsTestScenario scenario =
        JsTestScenario.builder()
            .bundleWithDeps(defaultBundleTarget)
            .arbitraryRule(locationTarget)
            .bundleGenrule(
                builderOptions(
                    String.format("$(location %s)", locationTarget.getFullyQualifiedName())))
            .build();

    BuildRule buildRule = scenario.resolver.requireRule(genruleTarget);
    assertThat(buildRule.getBuildDeps(), hasItem(scenario.resolver.getRule(locationTarget)));
  }

  @Test
  public void exposesRewrittenSourceMap() {
    setUpWithRewriteSourceMap();

    JsBundleGenrule genrule = setup.genrule();
    assertEquals(
        JsUtil.relativeToOutputRoot(
            genrule.getBuildTarget(),
            genrule.getProjectFilesystem(),
            JsUtil.getSourcemapPath(genrule)),
        genrule.getSourcePathToSourceMap());
  }

  @Test
  public void addsSourceMapAndSourceMapOutAsEnvironmentVariable() {
    setUpWithRewriteSourceMap();

    SourcePathResolver pathResolver = sourcePathResolver();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, builder);
    ImmutableMap<String, String> env = builder.build();

    assertThat(
        env,
        hasEntry(
            "SOURCEMAP",
            pathResolver.getAbsolutePath(setup.jsBundle().getSourcePathToSourceMap()).toString()));
    assertThat(
        env,
        hasEntry(
            "SOURCEMAP_OUT",
            pathResolver.getAbsolutePath(setup.genrule().getSourcePathToSourceMap()).toString()));
  }

  @Test
  public void specialSourceMapTargetPointsToOwnSourceMap() {
    setUpWithRewriteSourceMap(JsFlavors.SOURCE_MAP);

    DefaultSourcePathResolver pathResolver = sourcePathResolver();

    assertEquals(
        pathResolver.getRelativePath(setup.genrule().getSourcePathToSourceMap()),
        pathResolver.getRelativePath(setup.rule().getSourcePathToOutput()));
  }

  @Test
  public void createsSourcemapDir() {
    setUpWithRewriteSourceMap();

    JsBundleGenrule genrule = setup.genrule();
    BuildContext context = FakeBuildContext.withSourcePathResolver(sourcePathResolver());
    FakeBuildableContext buildableContext = new FakeBuildableContext();
    ImmutableList<Step> buildSteps =
        ImmutableList.copyOf(genrule.getBuildSteps(context, buildableContext));

    MkdirStep expectedStep =
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(),
                genrule.getProjectFilesystem(),
                context
                    .getSourcePathResolver()
                    .getRelativePath(genrule.getSourcePathToSourceMap())
                    .getParent()));
    assertThat(buildSteps, hasItem(expectedStep));

    int mkSourceMapDirIdx = buildSteps.indexOf(expectedStep);
    assertThat(
        buildSteps.subList(mkSourceMapDirIdx, buildSteps.size()), not(hasItem(any(RmStep.class))));
  }

  @Test
  public void recordsSourcemapArtifact() {
    setUpWithRewriteSourceMap();

    BuildContext context = FakeBuildContext.withSourcePathResolver(sourcePathResolver());
    FakeBuildableContext buildableContext = new FakeBuildableContext();
    setup.genrule().getBuildSteps(context, buildableContext);

    assertThat(
        buildableContext.getRecordedArtifacts(),
        hasItem(
            context
                .getSourcePathResolver()
                .getRelativePath(setup.genrule().getSourcePathToSourceMap())));
  }

  @Test
  public void exposesRewrittenMiscDir() {
    setUpWithRewriteMiscDir();

    JsBundleGenrule genrule = setup.genrule();
    assertEquals(
        JsUtil.relativeToOutputRoot(
            genrule.getBuildTarget(), genrule.getProjectFilesystem(), "misc"),
        genrule.getSourcePathToMisc());
  }

  @Test
  public void addsMiscAsEnvironmentVariable() {
    SourcePathResolver pathResolver = sourcePathResolver();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, builder);
    ImmutableMap<String, String> env = builder.build();

    assertThat(
        env,
        hasEntry(
            "MISC_DIR",
            pathResolver.getAbsolutePath(setup.genrule().getSourcePathToMisc()).toString()));
  }

  @Test
  public void addsMiscAndMiscOutAsEnvironmentVariableOnRewrite() {
    setUpWithRewriteMiscDir();

    SourcePathResolver pathResolver = sourcePathResolver();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    setup.genrule().addEnvironmentVariables(pathResolver, builder);
    ImmutableMap<String, String> env = builder.build();

    assertThat(
        env,
        hasEntry(
            "MISC_DIR",
            pathResolver.getAbsolutePath(setup.jsBundle().getSourcePathToMisc()).toString()));
    assertThat(
        env,
        hasEntry(
            "MISC_OUT",
            pathResolver.getAbsolutePath(setup.genrule().getSourcePathToMisc()).toString()));
  }

  @Test
  public void specialMiscTargetPointsToOwnMiscDir() {
    setUpWithRewriteMiscDir(JsFlavors.MISC);

    DefaultSourcePathResolver pathResolver = sourcePathResolver();

    assertEquals(
        pathResolver.getRelativePath(setup.genrule().getSourcePathToMisc()),
        pathResolver.getRelativePath(setup.rule().getSourcePathToOutput()));
  }

  @Test
  public void createsMiscDir() {
    setUpWithRewriteMiscDir();

    JsBundleGenrule genrule = setup.genrule();
    BuildContext context = FakeBuildContext.withSourcePathResolver(sourcePathResolver());
    FakeBuildableContext buildableContext = new FakeBuildableContext();
    ImmutableList<Step> buildSteps =
        ImmutableList.copyOf(genrule.getBuildSteps(context, buildableContext));

    MkdirStep expectedStep =
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(),
                genrule.getProjectFilesystem(),
                context.getSourcePathResolver().getRelativePath(genrule.getSourcePathToMisc())));
    assertThat(buildSteps, hasItem(expectedStep));

    int mkMiscDirIdx = buildSteps.indexOf(expectedStep);
    assertThat(
        buildSteps.subList(mkMiscDirIdx, buildSteps.size()), not(hasItem(any(RmStep.class))));
  }

  @Test
  public void recordsMiscDir() {
    setUpWithRewriteMiscDir();

    BuildContext context = FakeBuildContext.withSourcePathResolver(sourcePathResolver());
    FakeBuildableContext buildableContext = new FakeBuildableContext();
    setup.genrule().getBuildSteps(context, buildableContext);

    assertThat(
        buildableContext.getRecordedArtifacts(),
        hasItem(
            context
                .getSourcePathResolver()
                .getRelativePath(setup.genrule().getSourcePathToMisc())));
  }

  private JsBundleGenruleBuilder.Options builderOptions(BuildTarget bundleTarget, String cmd) {
    return JsBundleGenruleBuilder.Options.of(genruleTarget, bundleTarget).setCmd(cmd);
  }

  private JsBundleGenruleBuilder.Options builderOptions(BuildTarget bundleTarget) {
    return builderOptions(bundleTarget, null);
  }

  private JsBundleGenruleBuilder.Options builderOptions(String cmd) {
    return builderOptions(defaultBundleTarget, cmd);
  }

  private JsBundleGenruleBuilder.Options builderOptions() {
    return builderOptions(defaultBundleTarget);
  }

  private DefaultSourcePathResolver sourcePathResolver() {
    return DefaultSourcePathResolver.from(new SourcePathRuleFinder(setup.resolver()));
  }

  private static class TestSetup {
    private final JsTestScenario scenario;
    private final BuildTarget target;
    private final BuildTarget bundleTarget;

    TestSetup(JsTestScenario scenario, BuildTarget target, BuildTarget bundleTarget) {
      this.scenario = scenario;
      this.target = target;
      this.bundleTarget = bundleTarget;
    }

    BuildRule rule() {
      return scenario.resolver.requireRule(target);
    }

    JsBundleGenrule genrule() {
      return (JsBundleGenrule)
          scenario.resolver.requireRule(
              target.withoutFlavors(
                  JsFlavors.DEPENDENCY_FILE, JsFlavors.SOURCE_MAP, JsFlavors.MISC));
    }

    @SuppressWarnings("unchecked")
    TargetNode<JsBundleGenruleDescriptionArg, ?> targetNode() {
      TargetNode<?, ?> targetNode = scenario.targetGraph.get(target);
      return (TargetNode<JsBundleGenruleDescriptionArg, ?>) targetNode;
    }

    JsBundleOutputs jsBundle(Flavor... extraFlavors) {
      return (JsBundleOutputs)
          resolver().requireRule(bundleTarget.withAppendedFlavors(extraFlavors));
    }

    JsBundleAndroid jsBundleAndroid() {
      return resolver().getRuleWithType(bundleTarget, JsBundleAndroid.class);
    }

    BuildRule jsBundleDepsFile() {
      return resolver().requireRule(bundleTarget.withAppendedFlavors(JsFlavors.DEPENDENCY_FILE));
    }

    BuildRuleResolver resolver() {
      return scenario.resolver;
    }
  }

  private static AndroidPackageableCollector packageableCollectorMock(TestSetup setup) {
    AndroidPackageableCollector collector = EasyMock.createMock(AndroidPackageableCollector.class);
    expect(
            collector.addAssetsDirectory(
                setup.rule().getBuildTarget(), setup.genrule().getSourcePathToOutput()))
        .andReturn(collector);
    replay(collector);
    return collector;
  }
}
