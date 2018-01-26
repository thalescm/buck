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

package com.facebook.buck.go;

import com.facebook.buck.cxx.CxxBinaryDescription;
import com.facebook.buck.cxx.toolchain.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatforms;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.util.types.Either;
import com.facebook.buck.versions.VersionPropagator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.Optional;
import org.immutables.value.Value;

public class CgoLibraryDescription
    implements Description<CgoLibraryDescriptionArg>,
        ImplicitDepsInferringDescription<CgoLibraryDescriptionArg>,
        VersionPropagator<CgoLibraryDescriptionArg>,
        Flavored {

  private final GoBuckConfig goBuckConfig;
  private final CxxBuckConfig cxxBuckConfig;
  private final ToolchainProvider toolchainProvider;

  public CgoLibraryDescription(
      GoBuckConfig goBuckConfig, CxxBuckConfig cxxBuckConfig, ToolchainProvider toolchainProvider) {
    this.goBuckConfig = goBuckConfig;
    this.cxxBuckConfig = cxxBuckConfig;
    this.toolchainProvider = toolchainProvider;
  }

  @Override
  public Class<CgoLibraryDescriptionArg> getConstructorArgType() {
    return CgoLibraryDescriptionArg.class;
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return getGoToolchain().getPlatformFlavorDomain().containsAnyOf(flavors);
  }

  private GoToolchain getGoToolchain() {
    return toolchainProvider.getByName(GoToolchain.DEFAULT_NAME, GoToolchain.class);
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      CgoLibraryDescriptionArg args) {
    GoToolchain goToolchain = getGoToolchain();
    GoPlatform platform =
        goToolchain
            .getPlatformFlavorDomain()
            .getValue(buildTarget)
            .orElse(goToolchain.getDefaultPlatform());

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);

    return CGoLibrary.create(
        buildTarget,
        projectFilesystem,
        resolver,
        pathResolver,
        cellRoots,
        cxxBuckConfig,
        getCxxPlatform(),
        platform,
        args,
        args.getDeps(),
        goToolchain.getCGo(),
        args.getPackageName()
            .map(Paths::get)
            .orElse(goBuckConfig.getDefaultPackageName(buildTarget)));
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      CgoLibraryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    // Add the C/C++ linker parse time deps.
    CxxPlatform cxxPlatform = getCxxPlatform();
    targetGraphOnlyDepsBuilder.addAll(CxxPlatforms.getParseTimeDeps(cxxPlatform));
  }

  private CxxPlatform getCxxPlatform() {
    CxxPlatformsProvider cxxPlatformsProviderFactory =
        toolchainProvider.getByName(CxxPlatformsProvider.DEFAULT_NAME, CxxPlatformsProvider.class);
    return cxxPlatformsProviderFactory.getDefaultCxxPlatform();
  }

  @BuckStyleImmutable
  @Value.Immutable(copy = true)
  interface AbstractCgoLibraryDescriptionArg extends CxxBinaryDescription.CommonArg {
    ImmutableList<String> getCgoCompilerFlags();

    Optional<String> getPackageName();

    @Override
    @Value.Default
    default ImmutableList<StringWithMacros> getCompilerFlags() {
      // used for compilers other than gcc (due to __gcc_struct__)
      return wrapFlags(ImmutableList.of("-Wno-unknown-attributes"));
    }
  }

  private static ImmutableList<StringWithMacros> wrapFlags(ImmutableList<String> flags) {
    ImmutableList.Builder<StringWithMacros> builder = ImmutableList.builder();
    for (String flag : flags) {
      builder.add(StringWithMacros.of(ImmutableList.of(Either.ofLeft(flag))));
    }
    return builder.build();
  }
}
