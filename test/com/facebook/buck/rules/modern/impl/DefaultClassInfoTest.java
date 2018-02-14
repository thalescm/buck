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

package com.facebook.buck.rules.modern.impl;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SingleThreadedBuildRuleResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.keys.AlterRuleKeys;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ClassInfo;
import com.facebook.buck.rules.modern.InputRuleResolver;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.hamcrest.Matchers;
import org.junit.Test;

@SuppressWarnings("unused")
public class DefaultClassInfoTest {
  private InputRuleResolver inputRuleResolver = createStrictMock(InputRuleResolver.class);

  @SuppressWarnings("unchecked")
  private Consumer<BuildRule> buildRuleConsumer = createStrictMock(Consumer.class);

  @SuppressWarnings("unchecked")
  private BiConsumer<String, OutputPath> outputConsumer = createStrictMock(BiConsumer.class);

  private RuleKeyObjectSink ruleKeyObjectSink = createStrictMock(RuleKeyObjectSink.class);

  private ProjectFilesystem filesystem = new FakeProjectFilesystem();

  static class NoOpBuildable implements Buildable {
    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {
      return ImmutableList.of();
    }
  }

  private abstract static class BaseClass extends NoOpBuildable {
    private static final String BASE_NAME = "BaseClass";
    @AddToRuleKey private final SourcePath baseInputPath;
    @AddToRuleKey final OutputPath baseOutputPath;

    BaseClass(SourcePath inputPath) {
      this.baseInputPath = inputPath;
      this.baseOutputPath = new OutputPath("baseOutput");
    }
  }

  private static class DerivedClass extends BaseClass {
    private static final Optional<String> STRING = Optional.empty();
    @AddToRuleKey private final ImmutableList<SourcePath> inputs;
    @AddToRuleKey private final int value = 1;
    @AddToRuleKey private final long something = 2;
    @AddToRuleKey private final boolean enabled = true;

    DerivedClass(SourcePath baseInputPath, ImmutableList<SourcePath> inputs) {
      super(baseInputPath);
      this.inputs = inputs;
    }
  }

  @Test
  public void testDerivedClass() throws Exception {
    BuildTarget target1 = BuildTarget.of(Paths.get("some1"), "//some1", "name");
    BuildTarget target2 = BuildTarget.of(Paths.get("some2"), "//some2", "name");
    BuildTarget target3 = BuildTarget.of(Paths.get("some3"), "//some3", "name");

    BuildRule rule1 = new FakeBuildRule(target1, ImmutableSortedSet.of());
    BuildRule rule2 = new FakeBuildRule(target2, ImmutableSortedSet.of());
    BuildRule rule3 = new FakeBuildRule(target3, ImmutableSortedSet.of());

    BuildTargetSourcePath targetSourcePath1 =
        ExplicitBuildTargetSourcePath.of(target1, Paths.get("path"));
    BuildTargetSourcePath targetSourcePath2 =
        ExplicitBuildTargetSourcePath.of(target2, Paths.get("path"));
    BuildTargetSourcePath targetSourcePath3 =
        ExplicitBuildTargetSourcePath.of(target3, Paths.get("path"));

    PathSourcePath pathSourcePath = FakeSourcePath.of(filesystem, "path");

    DerivedClass buildable =
        new DerivedClass(
            targetSourcePath1,
            ImmutableList.of(targetSourcePath2, targetSourcePath3, pathSourcePath));
    ClassInfo<DerivedClass> classInfo = DefaultClassInfoFactory.forBuildable(buildable);
    assertEquals("derived_class", classInfo.getType());

    expect(
            ruleKeyObjectSink.setReflectively(
                ".class", "com.facebook.buck.rules.modern.impl.DefaultClassInfoTest$DerivedClass"))
        .andReturn(ruleKeyObjectSink);
    expect(ruleKeyObjectSink.setReflectively("enabled", true)).andReturn(ruleKeyObjectSink);
    expect(
            ruleKeyObjectSink.setReflectively(
                "inputs", ImmutableList.of(targetSourcePath2, targetSourcePath3, pathSourcePath)))
        .andReturn(ruleKeyObjectSink);
    expect(ruleKeyObjectSink.setReflectively("something", 2l)).andReturn(ruleKeyObjectSink);
    expect(ruleKeyObjectSink.setReflectively("value", 1)).andReturn(ruleKeyObjectSink);

    expect(ruleKeyObjectSink.setReflectively("baseInputPath", targetSourcePath1))
        .andReturn(ruleKeyObjectSink);
    expect(ruleKeyObjectSink.setReflectively("baseOutputPath", buildable.baseOutputPath))
        .andReturn(ruleKeyObjectSink);

    replay(ruleKeyObjectSink);
    AlterRuleKeys.amendKey(ruleKeyObjectSink, buildable);
    verify(ruleKeyObjectSink);

    expect(inputRuleResolver.resolve(targetSourcePath1)).andReturn(Optional.of(rule1));
    expect(inputRuleResolver.resolve(targetSourcePath2)).andReturn(Optional.of(rule2));
    expect(inputRuleResolver.resolve(targetSourcePath3)).andReturn(Optional.of(rule3));
    expect(inputRuleResolver.resolve(pathSourcePath)).andReturn(Optional.empty());

    buildRuleConsumer.accept(rule1);
    buildRuleConsumer.accept(rule2);
    buildRuleConsumer.accept(rule3);

    replay(inputRuleResolver, buildRuleConsumer);
    classInfo.computeDeps(buildable, inputRuleResolver, buildRuleConsumer);
    verify(inputRuleResolver, buildRuleConsumer);

    outputConsumer.accept("baseOutputPath", buildable.baseOutputPath);

    replay(outputConsumer);
    classInfo.getOutputs(buildable, outputConsumer);
    verify(outputConsumer);
  }

  @Test(expected = Exception.class)
  public void testLambdaBuildable() {
    try {
      DefaultClassInfoFactory.forBuildable(
          (buildContext, filesystem, outputPathResolver, buildCellPathFactory) -> null);
    } catch (Exception e) {
      assertThat(e.getMessage(), Matchers.containsString("cannot be synthetic"));
      assertThat(e.getMessage(), Matchers.containsString("DefaultClassInfoTest"));
      throw e;
    }
  }

  @Test(expected = Exception.class)
  public void testAnonymousBuildable() {
    try {
      DefaultClassInfoFactory.forBuildable(new NoOpBuildable() {});
    } catch (Exception e) {
      assertThat(e.getMessage(), Matchers.containsString("cannot be anonymous classes"));
      assertThat(e.getMessage(), Matchers.containsString("DefaultClassInfoTest"));
      throw e;
    }
  }

  @Test(expected = Exception.class)
  public void testLocalBuildable() {
    try {
      class LocalBuildable extends NoOpBuildable {}
      DefaultClassInfoFactory.forBuildable(new LocalBuildable());
    } catch (Exception e) {
      assertThat(e.getMessage(), Matchers.containsString("cannot be local classes"));
      assertThat(e.getMessage(), Matchers.containsString("LocalBuildable"));
      throw e;
    }
  }

  class NonStaticInnerBuildable extends NoOpBuildable {}

  @Test(expected = Exception.class)
  public void testNonStaticInner() {
    try {
      DefaultClassInfoFactory.forBuildable(new NonStaticInnerBuildable());
    } catch (Exception e) {
      assertThat(e.getMessage(), Matchers.containsString("cannot be inner non-static classes"));
      assertThat(e.getMessage(), Matchers.containsString("NonStaticInnerBuildable"));
      throw e;
    }
  }

  static class NonFinalFieldBuildable extends NoOpBuildable {
    int value = 0;
  }

  @Test(expected = Exception.class)
  public void testNonFinalField() {
    try {
      DefaultClassInfoFactory.forBuildable(new NonFinalFieldBuildable());
    } catch (Exception e) {
      assertThat(
          e.getMessage(), Matchers.containsString("must be final (NonFinalFieldBuildable.value)"));
      assertThat(e.getMessage(), Matchers.containsString("NonFinalFieldBuildable"));
      throw e;
    }
  }

  static class NonFinalStaticFieldBuildable extends NoOpBuildable {
    static int value = 0;
  }

  @Test(expected = Exception.class)
  public void testNonFinalStaticField() {
    try {
      DefaultClassInfoFactory.forBuildable(new NonFinalStaticFieldBuildable());
    } catch (Exception e) {
      assertThat(
          e.getMessage(),
          Matchers.containsString("must be final (NonFinalStaticFieldBuildable.value)"));
      assertThat(e.getMessage(), Matchers.containsString("NonFinalStaticFieldBuildable"));
      throw e;
    }
  }

  static class BadBase extends NoOpBuildable {
    int value = 0;
  }

  static class DerivedFromBadBased extends BadBase {}

  @Test(expected = Exception.class)
  public void testBadBase() {
    try {
      DefaultClassInfoFactory.forBuildable(new DerivedFromBadBased());
    } catch (Exception e) {
      assertThat(e.getMessage(), Matchers.containsString("must be final (BadBase.value)"));
      assertThat(e.getMessage(), Matchers.containsString("DerivedFromBadBased"));
      throw e;
    }
  }

  @Test
  public void testSimpleModernBuildRule() {
    // Just tests that we can construct a class info from a "direct" ModernBuildRule.
    DefaultClassInfoFactory.forBuildable(
        new NoOpModernBuildRule(
            BuildTargetFactory.newInstance("//some:target"),
            new FakeProjectFilesystem(),
            new SourcePathRuleFinder(
                new SingleThreadedBuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()))));
  }

  static class NoOpModernBuildRule extends ModernBuildRule<NoOpModernBuildRule>
      implements Buildable {
    NoOpModernBuildRule(
        BuildTarget buildTarget, ProjectFilesystem filesystem, SourcePathRuleFinder finder) {
      super(buildTarget, filesystem, finder, NoOpModernBuildRule.class);
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {
      return ImmutableList.of();
    }
  }
}
