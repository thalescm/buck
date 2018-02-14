/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.rules.macros;

import static com.facebook.buck.rules.TestCellBuilder.createCellRoots;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.macros.MacroException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildableSupport;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SingleThreadedBuildRuleResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.shell.ExportFileBuilder;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class ClasspathMacroExpanderTest {
  private ClasspathMacroExpander expander;
  private ProjectFilesystem filesystem;

  @Before
  public void createMacroExpander() {
    this.expander = new ClasspathMacroExpander();
    this.filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
  }

  @Test
  public void shouldIncludeARuleIfNothingIsGiven() throws Exception {
    final BuildRuleResolver buildRuleResolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(buildRuleResolver));
    BuildRule rule =
        getLibraryBuilder("//cheese:cake")
            .addSrc(Paths.get("Example.java")) // Force a jar to be created
            .build(buildRuleResolver, filesystem);

    assertExpandsTo(
        rule,
        buildRuleResolver,
        filesystem.getRootPath()
            + File.separator
            + pathResolver.getRelativePath(rule.getSourcePathToOutput()));
  }

  @Test
  public void shouldIncludeTransitiveDependencies() throws Exception {
    TargetNode<?, ?> depNode =
        getLibraryBuilder("//exciting:dep").addSrc(Paths.get("Dep.java")).build();

    TargetNode<?, ?> ruleNode =
        getLibraryBuilder("//exciting:target")
            .addSrc(Paths.get("Other.java"))
            .addDep(depNode.getBuildTarget())
            .build();

    TargetGraph targetGraph = TargetGraphFactory.newInstance(depNode, ruleNode);
    BuildRuleResolver ruleResolver =
        new SingleThreadedBuildRuleResolver(
            targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(ruleResolver));

    BuildRule rule = ruleResolver.requireRule(ruleNode.getBuildTarget());
    BuildRule dep = ruleResolver.requireRule(depNode.getBuildTarget());

    // Alphabetical sorting expected, so "dep" should be before "rule"
    assertExpandsTo(
        rule,
        ruleResolver,
        String.format(
            "%s" + File.separator + "%s" + File.pathSeparatorChar + "%s" + File.separator + "%s",
            filesystem.getRootPath(),
            pathResolver.getRelativePath(dep.getSourcePathToOutput()),
            filesystem.getRootPath(),
            pathResolver.getRelativePath(rule.getSourcePathToOutput())));
  }

  public JavaLibraryBuilder getLibraryBuilder(String target) {
    return JavaLibraryBuilder.createBuilder(
        BuildTargetFactory.newInstance(filesystem.getRootPath(), target), filesystem);
  }

  @Test(expected = MacroException.class)
  public void shouldThrowAnExceptionWhenRuleToExpandDoesNotHaveAClasspath() throws Exception {
    BuildRuleResolver ruleResolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(ruleResolver));
    BuildRule rule =
        new ExportFileBuilder(
                BuildTargetFactory.newInstance(filesystem.getRootPath(), "//cheese:peas"))
            .setSrc(FakeSourcePath.of("some-file.jar"))
            .build(ruleResolver);

    expander.expand(pathResolver, ClasspathMacro.of(rule.getBuildTarget()), rule);
  }

  @Test
  public void extractRuleKeyAppendables() throws Exception {
    TargetNode<?, ?> depNode =
        getLibraryBuilder("//exciting:dep").addSrc(Paths.get("Dep.java")).build();
    TargetNode<?, ?> ruleNode =
        getLibraryBuilder("//exciting:target")
            .addSrc(Paths.get("Other.java"))
            .addDep(depNode.getBuildTarget())
            .build();

    TargetGraph targetGraph = TargetGraphFactory.newInstance(depNode, ruleNode);
    BuildRuleResolver ruleResolver =
        new SingleThreadedBuildRuleResolver(
            targetGraph, new DefaultTargetNodeToBuildRuleTransformer());

    BuildRule rule = ruleResolver.requireRule(ruleNode.getBuildTarget());
    BuildRule dep = ruleResolver.requireRule(depNode.getBuildTarget());

    BuildTarget forTarget = BuildTargetFactory.newInstance("//:rule");
    CellPathResolver cellRoots = createCellRoots(filesystem);
    Arg ruleKeyAppendables =
        expander.expandFrom(
            forTarget,
            cellRoots,
            ruleResolver,
            expander.parse(
                forTarget, cellRoots, ImmutableList.of(rule.getBuildTarget().toString())));

    ImmutableList<BuildRule> deps =
        BuildableSupport.deriveDeps(ruleKeyAppendables, new SourcePathRuleFinder(ruleResolver))
            .collect(ImmutableList.toImmutableList());

    assertThat(deps, Matchers.equalTo(ImmutableList.of(dep, rule)));
  }

  private void assertExpandsTo(
      BuildRule rule, BuildRuleResolver buildRuleResolver, String expectedClasspath)
      throws MacroException {
    DefaultSourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(buildRuleResolver));
    String classpath =
        Arg.stringify(
            expander.expand(pathResolver, ClasspathMacro.of(rule.getBuildTarget()), rule),
            pathResolver);
    assertEquals(expectedClasspath, classpath);

    String expandedFile =
        Arg.stringify(
            expander.expandForFile(
                rule.getBuildTarget(),
                createCellRoots(filesystem),
                buildRuleResolver,
                ImmutableList.of(':' + rule.getBuildTarget().getShortName()),
                new Object()),
            pathResolver);
    assertTrue(expandedFile.startsWith("@"));
    Optional<String> fileContents =
        rule.getProjectFilesystem().readFileIfItExists(Paths.get(expandedFile.substring(1)));
    assertTrue(fileContents.isPresent());
    assertEquals(String.format("'%s'", expectedClasspath), fileContents.get());
  }
}
