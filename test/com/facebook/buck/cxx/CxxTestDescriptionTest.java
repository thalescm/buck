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

package com.facebook.buck.cxx;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.config.FakeBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatformUtils;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SingleThreadedBuildRuleResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.TestRule;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.rules.keys.TestDefaultRuleKeyFactory;
import com.facebook.buck.rules.macros.LocationMacro;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.test.TestRunningOptions;
import com.facebook.buck.test.selectors.TestSelectorList;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.cache.FileHashCacheMode;
import com.facebook.buck.util.cache.impl.DefaultFileHashCache;
import com.facebook.buck.util.cache.impl.StackedFileHashCache;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CxxTestDescriptionTest {

  @Parameterized.Parameters(name = "sandbox_sources={0}")
  public static Collection<Object[]> data() {
    return ImmutableList.of(new Object[] {false}, new Object[] {true});
  }

  private final ImmutableMap<String, ImmutableMap<String, String>> rawConfig;
  private final CxxBuckConfig cxxBuckConfig;

  public CxxTestDescriptionTest(boolean sandboxSources) {
    this.rawConfig =
        ImmutableMap.of(
            "cxx", ImmutableMap.of("sandbox_sources", Boolean.toString(sandboxSources)));
    this.cxxBuckConfig = new CxxBuckConfig(FakeBuckConfig.builder().setSections(rawConfig).build());
  }

  private void addSandbox(
      BuildRuleResolver resolver, ProjectFilesystem filesystem, BuildTarget libTarget)
      throws NoSuchBuildTargetException {
    BuildTarget target =
        libTarget.withAppendedFlavors(CxxLibraryDescription.Type.SANDBOX_TREE.getFlavor());
    createTestBuilder(target.toString()).build(resolver, filesystem);
  }

  private void addFramework(BuildRuleResolver resolver, ProjectFilesystem filesystem)
      throws NoSuchBuildTargetException {
    GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:framework_rule"))
        .setOut("out")
        .build(resolver, filesystem);
  }

  private CxxTestBuilder createTestBuilder() throws NoSuchBuildTargetException {
    return createTestBuilder("//:test");
  }

  private CxxTestBuilder createTestBuilder(String target) throws NoSuchBuildTargetException {
    return new CxxTestBuilder(
        BuildTargetFactory.newInstance(target),
        cxxBuckConfig,
        CxxPlatformUtils.DEFAULT_PLATFORM,
        CxxTestUtils.createDefaultPlatforms());
  }

  @Test
  public void findDepsFromParams() {
    BuildTarget gtest = BuildTargetFactory.newInstance("//:gtest");
    BuildTarget gtestMain = BuildTargetFactory.newInstance("//:gtest_main");

    CxxBuckConfig cxxBuckConfig =
        new CxxBuckConfig(
            FakeBuckConfig.builder()
                .setSections(rawConfig)
                .setSections(
                    ImmutableMap.of(
                        "cxx",
                        ImmutableMap.of(
                            "gtest_dep", gtest.toString(),
                            "gtest_default_test_main_dep", gtestMain.toString())))
                .build());

    BuildTarget target = BuildTargetFactory.newInstance("//:target");
    CxxTestBuilder builder =
        new CxxTestBuilder(target, cxxBuckConfig)
            .setFramework(CxxTestType.GTEST)
            .setUseDefaultTestMain(true);
    ImmutableSortedSet<BuildTarget> implicit = builder.findImplicitDeps();

    assertThat(implicit, hasItem(gtest));
    assertThat(implicit, hasItem(gtestMain));
  }

  @Test
  public void environmentIsPropagated() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    addFramework(resolver, filesystem);
    BuildRule someRule =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:some_rule"))
            .setOut("someRule")
            .build(resolver);
    CxxTestBuilder builder =
        createTestBuilder().setEnv(ImmutableMap.of("TEST", "value $(location //:some_rule)"));
    addSandbox(resolver, filesystem, builder.getTarget());
    CxxTest cxxTest = builder.build(resolver);
    TestRunningOptions options =
        TestRunningOptions.builder().setTestSelectorList(TestSelectorList.empty()).build();
    ImmutableList<Step> steps =
        cxxTest.runTests(
            TestExecutionContext.newInstance(),
            options,
            FakeBuildContext.withSourcePathResolver(pathResolver),
            TestRule.NOOP_REPORTING_CALLBACK);
    CxxTestStep testStep = (CxxTestStep) Iterables.getLast(steps);
    assertThat(
        testStep.getEnv(),
        Matchers.equalTo(
            Optional.of(
                ImmutableMap.of(
                    "TEST",
                    "value "
                        + pathResolver.getAbsolutePath(
                            Preconditions.checkNotNull(someRule.getSourcePathToOutput()))))));
  }

  @Test
  public void testArgsArePropagated() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    addFramework(resolver, filesystem);
    BuildRule someRule =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:some_rule"))
            .setOut("someRule")
            .build(resolver);
    CxxTestBuilder builder =
        createTestBuilder().setArgs(ImmutableList.of("value $(location //:some_rule)"));
    addSandbox(resolver, filesystem, builder.getTarget());
    CxxTest cxxTest = builder.build(resolver);
    TestRunningOptions testOptions =
        TestRunningOptions.builder()
            .setShufflingTests(false)
            .setTestSelectorList(TestSelectorList.empty())
            .build();
    ImmutableList<Step> steps =
        cxxTest.runTests(
            TestExecutionContext.newInstance(),
            testOptions,
            FakeBuildContext.withSourcePathResolver(pathResolver),
            TestRule.NOOP_REPORTING_CALLBACK);
    CxxTestStep testStep = (CxxTestStep) Iterables.getLast(steps);
    assertThat(
        testStep.getCommand(),
        hasItem(
            "value "
                + pathResolver.getAbsolutePath(
                    Preconditions.checkNotNull(someRule.getSourcePathToOutput()))));
  }

  @Test
  public void runTestSeparately() throws Exception {
    for (CxxTestType framework : CxxTestType.values()) {
      ProjectFilesystem filesystem = new FakeProjectFilesystem();
      BuildRuleResolver resolver =
          new SingleThreadedBuildRuleResolver(
              TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
      addFramework(resolver, filesystem);
      CxxTestBuilder builder =
          createTestBuilder()
              .setRunTestSeparately(true)
              .setUseDefaultTestMain(true)
              .setFramework(framework);
      addSandbox(resolver, filesystem, builder.getTarget());
      CxxTest cxxTest = builder.build(resolver);
      assertTrue(cxxTest.runTestSeparately());
    }
  }

  @Test
  public void runtimeDepOnDeps() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildTarget cxxBinaryTarget = BuildTargetFactory.newInstance("//:dep");
    BuildTarget cxxLibraryTarget = BuildTargetFactory.newInstance("//:lib");
    CxxBinaryBuilder cxxBinaryBuilder = new CxxBinaryBuilder(cxxBinaryTarget);
    CxxLibraryBuilder cxxLibraryBuilder =
        new CxxLibraryBuilder(cxxLibraryTarget).setDeps(ImmutableSortedSet.of(cxxBinaryTarget));
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraphFactory.newInstance(cxxLibraryBuilder.build(), cxxBinaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    addFramework(resolver, filesystem);
    BuildRule cxxBinary = cxxBinaryBuilder.build(resolver, filesystem);
    cxxLibraryBuilder.build(resolver, filesystem);
    CxxTestBuilder cxxTestBuilder =
        createTestBuilder().setDeps(ImmutableSortedSet.of(cxxLibraryTarget));
    addSandbox(resolver, filesystem, cxxTestBuilder.getTarget());
    CxxTest cxxTest = cxxTestBuilder.build(resolver, filesystem);
    assertThat(
        BuildRules.getTransitiveRuntimeDeps(cxxTest, resolver),
        hasItem(cxxBinary.getBuildTarget()));
  }

  @Test
  public void locationMacro() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    Genrule dep =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setOut("out")
            .build(resolver);
    CxxTestBuilder builder =
        createTestBuilder()
            .setLinkerFlags(
                ImmutableList.of(
                    StringWithMacrosUtils.format(
                        "--linker-script=%s", LocationMacro.of(dep.getBuildTarget()))));
    addFramework(resolver, filesystem);
    addSandbox(resolver, filesystem, builder.getTarget());
    assertThat(builder.build().getExtraDeps(), hasItem(dep.getBuildTarget()));
    CxxTest test = builder.build(resolver);
    CxxLink binary =
        (CxxLink)
            resolver.getRule(
                CxxDescriptionEnhancer.createCxxLinkTarget(
                    test.getBuildTarget(), Optional.empty()));
    assertThat(
        Arg.stringify(binary.getArgs(), pathResolver),
        hasItem(String.format("--linker-script=%s", dep.getAbsoluteOutputFilePath())));
    assertThat(binary.getBuildDeps(), hasItem(dep));
  }

  @Test
  public void linkerFlagsLocationMacro() throws Exception {
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    Genrule dep =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setOut("out")
            .build(resolver);
    CxxTestBuilder builder =
        createTestBuilder("//:rule")
            .setLinkerFlags(
                ImmutableList.of(
                    StringWithMacrosUtils.format(
                        "--linker-script=%s", LocationMacro.of(dep.getBuildTarget()))));
    assertThat(builder.build().getExtraDeps(), hasItem(dep.getBuildTarget()));
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    addFramework(resolver, filesystem);
    addSandbox(resolver, filesystem, builder.getTarget());
    CxxTest test = builder.build(resolver);
    CxxLink binary =
        (CxxLink)
            resolver.getRule(
                CxxDescriptionEnhancer.createCxxLinkTarget(
                    test.getBuildTarget(), Optional.empty()));
    assertThat(binary, Matchers.instanceOf(CxxLink.class));
    assertThat(
        Arg.stringify(binary.getArgs(), pathResolver),
        hasItem(String.format("--linker-script=%s", dep.getAbsoluteOutputFilePath())));
    assertThat(binary.getBuildDeps(), hasItem(dep));
  }

  @Test
  public void platformLinkerFlagsLocationMacroWithMatch() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    Genrule dep =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setOut("out")
            .build(resolver);
    CxxTestBuilder builder =
        createTestBuilder()
            .setPlatformLinkerFlags(
                new PatternMatchedCollection.Builder<ImmutableList<StringWithMacros>>()
                    .add(
                        Pattern.compile(
                            Pattern.quote(
                                CxxPlatformUtils.DEFAULT_PLATFORM.getFlavor().toString())),
                        ImmutableList.of(
                            StringWithMacrosUtils.format(
                                "--linker-script=%s", LocationMacro.of(dep.getBuildTarget()))))
                    .build());
    addFramework(resolver, filesystem);
    addSandbox(resolver, filesystem, builder.getTarget());
    assertThat(builder.build().getExtraDeps(), hasItem(dep.getBuildTarget()));
    CxxTest test = builder.build(resolver);
    CxxLink binary =
        (CxxLink)
            resolver.getRule(
                CxxDescriptionEnhancer.createCxxLinkTarget(
                    test.getBuildTarget(), Optional.empty()));
    assertThat(
        Arg.stringify(binary.getArgs(), pathResolver),
        hasItem(String.format("--linker-script=%s", dep.getAbsoluteOutputFilePath())));
    assertThat(binary.getBuildDeps(), hasItem(dep));
  }

  @Test
  public void platformLinkerFlagsLocationMacroWithoutMatch() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    Genrule dep =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setOut("out")
            .build(resolver);
    addFramework(resolver, filesystem);
    CxxTestBuilder builder =
        createTestBuilder()
            .setPlatformLinkerFlags(
                new PatternMatchedCollection.Builder<ImmutableList<StringWithMacros>>()
                    .add(
                        Pattern.compile("nothing matches this string"),
                        ImmutableList.of(
                            StringWithMacrosUtils.format(
                                "--linker-script=%s", LocationMacro.of(dep.getBuildTarget()))))
                    .build());
    assertThat(builder.build().getExtraDeps(), hasItem(dep.getBuildTarget()));
    addSandbox(resolver, filesystem, builder.getTarget());
    CxxTest test = builder.build(resolver);
    CxxLink binary =
        (CxxLink)
            resolver.getRule(
                CxxDescriptionEnhancer.createCxxLinkTarget(
                    test.getBuildTarget(), Optional.empty()));
    assertThat(
        Arg.stringify(binary.getArgs(), pathResolver),
        Matchers.not(
            hasItem(String.format("--linker-script=%s", dep.getAbsoluteOutputFilePath()))));
    assertThat(binary.getBuildDeps(), Matchers.not(hasItem(dep)));
  }

  @Test
  public void resourcesAffectRuleKey() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    Path resource = filesystem.getPath("resource");
    filesystem.touch(resource);
    for (CxxTestType framework : CxxTestType.values()) {
      // Create a test rule without resources attached.
      BuildRuleResolver resolver =
          new SingleThreadedBuildRuleResolver(
              TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
      addFramework(resolver, filesystem);
      CxxTestBuilder builder = createTestBuilder().setFramework(framework);
      addSandbox(resolver, filesystem, builder.getTarget());
      CxxTest cxxTestWithoutResources = builder.build(resolver, filesystem);
      RuleKey ruleKeyWithoutResource = getRuleKey(resolver, cxxTestWithoutResources);

      // Create a rule with a resource attached.
      resolver =
          new SingleThreadedBuildRuleResolver(
              TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
      addFramework(resolver, filesystem);
      builder =
          createTestBuilder().setFramework(framework).setResources(ImmutableSortedSet.of(resource));
      addSandbox(resolver, filesystem, builder.getTarget());
      CxxTest cxxTestWithResources = builder.build(resolver, filesystem);
      RuleKey ruleKeyWithResource = getRuleKey(resolver, cxxTestWithResources);

      // Verify that their rule keys are different.
      assertThat(ruleKeyWithoutResource, Matchers.not(Matchers.equalTo(ruleKeyWithResource)));
    }
  }

  @Test
  public void resourcesAreInputs() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    Path resource = filesystem.getPath("resource");
    filesystem.touch(resource);
    for (CxxTestType framework : CxxTestType.values()) {
      TargetNode<?, ?> cxxTestWithResources =
          createTestBuilder()
              .setFramework(framework)
              .setResources(ImmutableSortedSet.of(resource))
              .build();
      assertThat(cxxTestWithResources.getInputs(), hasItem(resource));
    }
  }

  private RuleKey getRuleKey(BuildRuleResolver resolver, BuildRule rule) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);
    FileHashCache fileHashCache =
        new StackedFileHashCache(
            ImmutableList.of(
                DefaultFileHashCache.createDefaultFileHashCache(
                    rule.getProjectFilesystem(), FileHashCacheMode.DEFAULT)));
    DefaultRuleKeyFactory factory =
        new TestDefaultRuleKeyFactory(fileHashCache, pathResolver, ruleFinder);
    return factory.build(rule);
  }
}
