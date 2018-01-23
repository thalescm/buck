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

package com.facebook.buck.android;

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.HasJavaAbi;
import com.facebook.buck.jvm.core.JavaLibrary;
import com.facebook.buck.jvm.java.CalculateClassAbi;
import com.facebook.buck.jvm.java.DefaultJavaLibrary;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.java.JavaTest;
import com.facebook.buck.jvm.java.JavaTestDescription;
import com.facebook.buck.jvm.java.JavacFactory;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacOptionsFactory;
import com.facebook.buck.jvm.java.TestType;
import com.facebook.buck.jvm.java.toolchain.JavaOptionsProvider;
import com.facebook.buck.jvm.java.toolchain.JavacOptionsProvider;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.macros.MacroException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.macros.LocationMacroExpander;
import com.facebook.buck.rules.macros.MacroArg;
import com.facebook.buck.rules.macros.MacroHandler;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.DependencyMode;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import org.immutables.value.Value;

public class RobolectricTestDescription
    implements Description<RobolectricTestDescriptionArg>,
        ImplicitDepsInferringDescription<
            RobolectricTestDescription.AbstractRobolectricTestDescriptionArg> {

  private static final MacroHandler MACRO_HANDLER =
      new MacroHandler(ImmutableMap.of("location", new LocationMacroExpander()));


  private final ToolchainProvider toolchainProvider;
  private final JavaBuckConfig javaBuckConfig;
  private final AndroidLibraryCompilerFactory compilerFactory;

  public RobolectricTestDescription(
      ToolchainProvider toolchainProvider,
      JavaBuckConfig javaBuckConfig,
      AndroidLibraryCompilerFactory compilerFactory) {
    this.toolchainProvider = toolchainProvider;
    this.javaBuckConfig = javaBuckConfig;
    this.compilerFactory = compilerFactory;
  }

  @Override
  public Class<RobolectricTestDescriptionArg> getConstructorArgType() {
    return RobolectricTestDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      RobolectricTestDescriptionArg args) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);

    if (HasJavaAbi.isClassAbiTarget(buildTarget)) {
      Preconditions.checkArgument(
          !buildTarget.getFlavors().contains(AndroidLibraryGraphEnhancer.DUMMY_R_DOT_JAVA_FLAVOR));
      BuildTarget testTarget = HasJavaAbi.getLibraryTarget(buildTarget);
      BuildRule testRule = resolver.requireRule(testTarget);
      return CalculateClassAbi.of(
          buildTarget,
          ruleFinder,
          projectFilesystem,
          params,
          Preconditions.checkNotNull(testRule.getSourcePathToOutput()));
    }

    JavacOptions javacOptions =
        JavacOptionsFactory.create(
            toolchainProvider
                .getByName(JavacOptionsProvider.DEFAULT_NAME, JavacOptionsProvider.class)
                .getJavacOptions(),
            buildTarget,
            projectFilesystem,
            resolver,
            args);

    AndroidLibraryGraphEnhancer graphEnhancer =
        new AndroidLibraryGraphEnhancer(
            buildTarget,
            projectFilesystem,
            ImmutableSortedSet.copyOf(
                Iterables.concat(
                    params.getBuildDeps(), resolver.getAllRules(args.getExportedDeps()))),
            JavacFactory.create(ruleFinder, javaBuckConfig, args),
            javacOptions,
            DependencyMode.TRANSITIVE,
            args.isForceFinalResourceIds(),
            args.getResourceUnionPackage(),
            /* rName */ Optional.empty(),
            args.isUseOldStyleableFormat(),
            /* skipNonUnionRDotJava */ false);

    ImmutableList<String> vmArgs = args.getVmArgs();

    Optional<DummyRDotJava> dummyRDotJava =
        graphEnhancer.getBuildableForAndroidResources(resolver, /* createBuildableIfEmpty */ true);
    RobolectricTestDescriptionArg testLibraryArgs = args;

    if (dummyRDotJava.isPresent()) {
      ImmutableSortedSet<BuildRule> newDeclaredDeps =
          ImmutableSortedSet.<BuildRule>naturalOrder()
              .addAll(params.getDeclaredDeps().get())
              .add(dummyRDotJava.get())
              .build();
      params = params.withDeclaredDeps(newDeclaredDeps);
      testLibraryArgs =
          testLibraryArgs.withDeps(
              Iterables.concat(
                  args.getDeps(), Collections.singletonList(dummyRDotJava.get().getBuildTarget())));
    }

    JavaTestDescription.CxxLibraryEnhancement cxxLibraryEnhancement =
        new JavaTestDescription.CxxLibraryEnhancement(
            buildTarget,
            projectFilesystem,
            params,
            args.getUseCxxLibraries(),
            args.getCxxLibraryWhitelist(),
            resolver,
            ruleFinder,
            toolchainProvider
                .getByName(CxxPlatformsProvider.DEFAULT_NAME, CxxPlatformsProvider.class)
                .getDefaultCxxPlatform());
    params = cxxLibraryEnhancement.updatedParams;

    BuildTarget testLibraryBuildTarget =
        buildTarget.withAppendedFlavors(JavaTest.COMPILED_TESTS_LIBRARY_FLAVOR);

    JavaLibrary testsLibrary =
        resolver.addToIndex(
            DefaultJavaLibrary.rulesBuilder(
                    testLibraryBuildTarget,
                    projectFilesystem,
                    params,
                    resolver,
                    compilerFactory.getCompiler(
                        args.getLanguage().orElse(AndroidLibraryDescription.JvmLanguage.JAVA)),
                    javaBuckConfig,
                    testLibraryArgs)
                .setJavacOptions(javacOptions)
                .build()
                .buildLibrary());

    Function<String, Arg> toMacroArgFunction =
        MacroArg.toMacroArgFunction(MACRO_HANDLER, buildTarget, cellRoots, resolver);

    AndroidPlatformTarget androidPlatformTarget =
        toolchainProvider.getByName(
            AndroidPlatformTarget.DEFAULT_NAME, AndroidPlatformTarget.class);

    return new RobolectricTest(
        buildTarget,
        projectFilesystem,
        params.withDeclaredDeps(ImmutableSortedSet.of(testsLibrary)).withoutExtraDeps(),
        androidPlatformTarget,
        testsLibrary,
        args.getLabels(),
        args.getContacts(),
        TestType.JUNIT,
        toolchainProvider
            .getByName(JavaOptionsProvider.DEFAULT_NAME, JavaOptionsProvider.class)
            .getJavaOptionsForTests(),
        vmArgs,
        cxxLibraryEnhancement.nativeLibsEnvironment,
        dummyRDotJava,
        args.getTestRuleTimeoutMs()
            .map(Optional::of)
            .orElse(javaBuckConfig.getDelegate().getDefaultTestRuleTimeoutMs()),
        args.getTestCaseTimeoutMs(),
        ImmutableMap.copyOf(Maps.transformValues(args.getEnv(), toMacroArgFunction::apply)),
        args.getRunTestSeparately(),
        args.getForkMode(),
        args.getStdOutLogLevel(),
        args.getStdErrLogLevel(),
        args.getUnbundledResourcesRoot(),
        args.getRobolectricRuntimeDependency(),
        args.getRobolectricManifest(),
        javaBuckConfig
            .getDelegate()
            .getBooleanValue("test", "pass_robolectric_directories_in_file", false));
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      AbstractRobolectricTestDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    for (String envValue : constructorArg.getEnv().values()) {
      try {
        MACRO_HANDLER.extractParseTimeDeps(
            buildTarget, cellRoots, envValue, extraDepsBuilder, targetGraphOnlyDepsBuilder);
      } catch (MacroException e) {
        throw new HumanReadableException(e, "%s: %s", buildTarget, e.getMessage());
      }
    }
  }

  @BuckStyleImmutable
  @Value.Immutable(copy = true)
  interface AbstractRobolectricTestDescriptionArg
      extends JavaTestDescription.CoreArg, AndroidKotlinCoreArg {

    Optional<String> getRobolectricRuntimeDependency();

    Optional<SourcePath> getRobolectricManifest();

    Optional<String> getResourceUnionPackage();

    @Value.Default
    default boolean isUseOldStyleableFormat() {
      return false;
    }

    @Value.Default
    default boolean isForceFinalResourceIds() {
      return true;
    }

  }
}
