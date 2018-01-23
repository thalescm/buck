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

package com.facebook.buck.shell;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.SingleThreadedBuildRuleResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.macros.LocationMacro;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class CommandAliasBuilder
    extends AbstractNodeBuilder<
        CommandAliasDescriptionArg.Builder, CommandAliasDescriptionArg, CommandAliasDescription,
        CommandAlias> {

  private static final CommandAliasDescription aliasBinaryDescription =
      new CommandAliasDescription(Platform.UNKNOWN);
  private final CommandAliasDescription commandAliasDescription;
  private final Set<TargetNode<?, ?>> nodes;
  private final ImmutableSortedMap.Builder<Platform, BuildTarget> platformExeBuilder =
      ImmutableSortedMap.naturalOrder();

  private CommandAliasBuilder(
      BuildTarget target, CommandAliasDescription description, Set<TargetNode<?, ?>> nodes) {
    super(description, target);
    this.commandAliasDescription = description;
    this.nodes = nodes;
  }

  public CommandAliasBuilder(BuildTarget target) {
    this(target, aliasBinaryDescription);
  }

  public CommandAliasBuilder(BuildTarget target, CommandAliasDescription description) {
    this(target, description, new LinkedHashSet<>());
  }

  public CommandAliasBuilder subBuilder(BuildTarget target) {
    return new CommandAliasBuilder(target, commandAliasDescription, nodes);
  }

  public CommandAliasBuilder setExe(BuildTarget exe) {
    addBuildRule(exe);
    getArgForPopulating().setExe(exe);
    return this;
  }

  public CommandAliasBuilder setExe(TargetNode<?, ?> commandNode) {
    nodes.add(commandNode);
    getArgForPopulating().setExe(commandNode.getBuildTarget());
    return this;
  }

  public CommandAliasBuilder setStringArgs(String... stringArgs) {
    return setArgs(StringWithMacrosUtils.fromStrings(Arrays.asList(stringArgs)));
  }

  public CommandAliasBuilder setArgs(StringWithMacros... args) {
    return setArgs(Arrays.asList(args));
  }

  private CommandAliasBuilder setArgs(Collection<StringWithMacros> args) {
    getArgForPopulating().setArgs(args);
    addTargetsForMacros(args.stream());
    return this;
  }

  public CommandAliasBuilder setEnv(Map<String, StringWithMacros> env) {
    addTargetsForMacros(env.values().stream());
    getArgForPopulating().setEnv(env);
    return this;
  }

  private void addTargetsForMacros(Stream<StringWithMacros> values) {
    RichStream.from(
            values
                .map(stringWithMacros -> stringWithMacros.getMacros())
                .flatMap(Collection::stream))
        .filter(LocationMacro.class)
        .map(LocationMacro::getTarget)
        .forEach(this::addTarget);
  }

  public CommandAliasBuilder setPlatformExe(Map<Platform, BuildTarget> platformExe) {
    platformExe.values().forEach(this::addBuildRule);
    platformExeBuilder.putAll(platformExe);
    getArgForPopulating().setPlatformExe(platformExeBuilder.build());
    return this;
  }

  public CommandAliasBuilder setPlatformExe(Platform platform, TargetNode<?, ?> commandNode) {
    nodes.add(commandNode);
    platformExeBuilder.put(platform, commandNode.getBuildTarget());
    getArgForPopulating().setPlatformExe(platformExeBuilder.build());
    return this;
  }

  public CommandAliasBuilder addTarget(BuildTarget target) {
    return addTarget(
        GenruleBuilder.newGenruleBuilder(target, filesystem).setOut("arbitrary-file").build());
  }

  public CommandAliasBuilder addTarget(TargetNode<?, ?> targetNode) {
    nodes.add(targetNode);
    return this;
  }

  public BuildResult buildResult() {
    nodes.add(build());
    TargetGraph graph = TargetGraphFactory.newInstance(nodes);
    BuildRuleResolver resolver =
        new SingleThreadedBuildRuleResolver(graph, new DefaultTargetNodeToBuildRuleTransformer());

    return new BuildResult(
        (CommandAlias) resolver.requireRule(getTarget()), getPopulatedArg(), resolver, cellRoots);
  }

  public BuildTarget addBuildRule(BuildTarget target) {
    nodes.add(GenruleBuilder.newGenruleBuilder(target, filesystem).setOut("out").build());
    return target;
  }

  public static class BuildResult {
    private final CommandAlias commandAlias;
    private final SourcePathResolver sourcePathResolver;
    private final BuildRuleResolver resolver;
    private final CommandAliasDescriptionArg arg;
    private final SourcePathRuleFinder ruleFinder;
    private final CellPathResolver cellRoots;

    BuildResult(
        CommandAlias commandAlias,
        CommandAliasDescriptionArg arg,
        BuildRuleResolver resolver,
        CellPathResolver cellRoots) {
      this.commandAlias = commandAlias;
      this.arg = arg;
      ruleFinder = new SourcePathRuleFinder(resolver);
      this.cellRoots = cellRoots;
      sourcePathResolver = DefaultSourcePathResolver.from(this.ruleFinder);
      this.resolver = resolver;
    }

    CommandAlias commandAlias() {
      return commandAlias;
    }

    SourcePathResolver sourcePathResolver() {
      return sourcePathResolver;
    }

    public String pathOf(BuildTarget target) {
      return sourcePathResolver
          .getAbsolutePath(resolver.requireRule(target).getSourcePathToOutput())
          .toString();
    }

    public ImmutableList<String> exeOf(BuildTarget target) {
      return resolver
          .getRuleWithType(target, BinaryBuildRule.class)
          .getExecutableCommand()
          .getCommandPrefix(sourcePathResolver);
    }

    public BuildRuleResolver resolver() {
      return resolver;
    }

    public CommandAliasDescriptionArg arg() {
      return arg;
    }

    public SourcePathRuleFinder ruleFinder() {
      return ruleFinder;
    }

    public CellPathResolver cellRoots() {
      return cellRoots;
    }

    public ImmutableList<String> getCommandPrefix() {
      return commandAlias.getExecutableCommand().getCommandPrefix(sourcePathResolver);
    }

    public ImmutableMap<String, String> getEnvironment() {
      return commandAlias.getExecutableCommand().getEnvironment(sourcePathResolver);
    }

    public Iterable<BuildTarget> getRuntimeDeps() {
      return commandAlias.getRuntimeDeps(ruleFinder).collect(ImmutableList.toImmutableList());
    }
  }
}
