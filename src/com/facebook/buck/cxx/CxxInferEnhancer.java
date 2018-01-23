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

package com.facebook.buck.cxx;

import com.facebook.buck.cxx.toolchain.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.InferBuckConfig;
import com.facebook.buck.cxx.toolchain.PicType;
import com.facebook.buck.graph.AbstractBreadthFirstTraversal;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorConvertible;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SymlinkTree;
import com.facebook.buck.util.RichStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import java.nio.file.Path;
import java.util.Optional;

/** Handles infer flavors for {@link CxxLibrary} and {@link CxxBinary}. */
public final class CxxInferEnhancer {

  /** Flavor adorning the individual inter capture rules. */
  static final InternalFlavor INFER_CAPTURE_FLAVOR = InternalFlavor.of("infer-capture");

  /** Flavors affixed to a library or binary rule to run infer. */
  public enum InferFlavors implements FlavorConvertible {
    INFER(InternalFlavor.of("infer")),
    INFER_ANALYZE(InternalFlavor.of("infer-analyze")),
    INFER_CAPTURE_ALL(InternalFlavor.of("infer-capture-all")),
    INFER_CAPTURE_ONLY(InternalFlavor.of("infer-capture-only"));

    private final InternalFlavor flavor;

    InferFlavors(InternalFlavor flavor) {
      this.flavor = flavor;
    }

    @Override
    public InternalFlavor getFlavor() {
      return flavor;
    }

    private static BuildTarget targetWithoutAnyInferFlavor(BuildTarget target) {
      BuildTarget result = target;
      for (InferFlavors f : values()) {
        result = result.withoutFlavors(f.getFlavor());
      }
      return result;
    }

    private static void checkNoInferFlavors(ImmutableSet<Flavor> flavors) {
      for (InferFlavors f : InferFlavors.values()) {
        Preconditions.checkArgument(
            !flavors.contains(f.getFlavor()),
            "Unexpected infer-related flavor found: %s",
            f.toString());
      }
    }
  }

  public static FlavorDomain<InferFlavors> INFER_FLAVOR_DOMAIN =
      FlavorDomain.from("Infer flavors", InferFlavors.class);

  public static BuildRule requireInferRule(
      BuildTarget target,
      ProjectFilesystem filesystem,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform cxxPlatform,
      CxxConstructorArg args,
      InferBuckConfig inferBuckConfig) {
    return new CxxInferEnhancer(resolver, cxxBuckConfig, inferBuckConfig, cxxPlatform)
        .requireInferRule(target, cellRoots, filesystem, args);
  }

  private final BuildRuleResolver ruleResolver;
  private final CxxBuckConfig cxxBuckConfig;
  private final InferBuckConfig inferBuckConfig;
  private final CxxPlatform cxxPlatform;

  private CxxInferEnhancer(
      BuildRuleResolver ruleResolver,
      CxxBuckConfig cxxBuckConfig,
      InferBuckConfig inferBuckConfig,
      CxxPlatform cxxPlatform) {
    this.ruleResolver = ruleResolver;
    this.cxxBuckConfig = cxxBuckConfig;
    this.inferBuckConfig = inferBuckConfig;
    this.cxxPlatform = cxxPlatform;
  }

  private BuildRule requireInferRule(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {
    Optional<InferFlavors> inferFlavor = INFER_FLAVOR_DOMAIN.getValue(buildTarget);
    Preconditions.checkArgument(
        inferFlavor.isPresent(), "Expected BuildRuleParams to contain infer flavor.");
    switch (inferFlavor.get()) {
      case INFER:
        return requireInferAnalyzeAndReportBuildRuleForCxxDescriptionArg(
            buildTarget, cellRoots, filesystem, args);
      case INFER_ANALYZE:
        return requireInferAnalyzeBuildRuleForCxxDescriptionArg(
            buildTarget, cellRoots, filesystem, args);
      case INFER_CAPTURE_ALL:
        return requireAllTransitiveCaptureBuildRules(buildTarget, cellRoots, filesystem, args);
      case INFER_CAPTURE_ONLY:
        return requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
            buildTarget, cellRoots, filesystem, args);
    }
    throw new IllegalStateException(
        "All InferFlavor cases should be handled, got: " + inferFlavor.get());
  }

  private BuildRule requireAllTransitiveCaptureBuildRules(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {

    CxxInferCaptureRulesAggregator aggregator =
        requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
            target, cellRoots, filesystem, args);

    ImmutableSet<CxxInferCapture> captureRules = aggregator.getAllTransitiveCaptures();

    return ruleResolver.addToIndex(new CxxInferCaptureTransitive(target, filesystem, captureRules));
  }

  private CxxInferComputeReport requireInferAnalyzeAndReportBuildRuleForCxxDescriptionArg(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {

    BuildTarget cleanTarget = InferFlavors.targetWithoutAnyInferFlavor(target);

    return (CxxInferComputeReport)
        ruleResolver.computeIfAbsent(
            cleanTarget.withAppendedFlavors(InferFlavors.INFER.getFlavor()),
            targetWithInferFlavor ->
                new CxxInferComputeReport(
                    targetWithInferFlavor,
                    filesystem,
                    requireInferAnalyzeBuildRuleForCxxDescriptionArg(
                        cleanTarget, cellRoots, filesystem, args)));
  }

  private CxxInferAnalyze requireInferAnalyzeBuildRuleForCxxDescriptionArg(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {

    Flavor inferAnalyze = InferFlavors.INFER_ANALYZE.getFlavor();

    BuildTarget cleanTarget = InferFlavors.targetWithoutAnyInferFlavor(target);

    return (CxxInferAnalyze)
        ruleResolver.computeIfAbsent(
            cleanTarget.withAppendedFlavors(inferAnalyze),
            targetWithInferAnalyzeFlavor -> {
              ImmutableSet<BuildRule> deps = args.getCxxDeps().get(ruleResolver, cxxPlatform);
              ImmutableSet<CxxInferAnalyze> transitiveDepsLibraryRules =
                  requireTransitiveDependentLibraries(
                      cxxPlatform, deps, inferAnalyze, CxxInferAnalyze.class);
              return new CxxInferAnalyze(
                  targetWithInferAnalyzeFlavor,
                  filesystem,
                  inferBuckConfig,
                  requireInferCaptureBuildRules(
                      cleanTarget, cellRoots, filesystem, collectSources(cleanTarget, args), args),
                  transitiveDepsLibraryRules);
            });
  }

  private CxxInferCaptureRulesAggregator requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {

    Flavor inferCaptureOnly = InferFlavors.INFER_CAPTURE_ONLY.getFlavor();

    return (CxxInferCaptureRulesAggregator)
        ruleResolver.computeIfAbsent(
            InferFlavors.targetWithoutAnyInferFlavor(target).withAppendedFlavors(inferCaptureOnly),
            targetWithInferCaptureOnlyFlavor -> {
              BuildTarget cleanTarget = InferFlavors.targetWithoutAnyInferFlavor(target);

              ImmutableMap<String, CxxSource> sources = collectSources(cleanTarget, args);

              ImmutableSet<CxxInferCapture> captureRules =
                  requireInferCaptureBuildRules(cleanTarget, cellRoots, filesystem, sources, args);

              ImmutableSet<CxxInferCaptureRulesAggregator> transitiveAggregatorRules =
                  requireTransitiveCaptureAndAggregatingRules(args, inferCaptureOnly);

              return new CxxInferCaptureRulesAggregator(
                  targetWithInferCaptureOnlyFlavor,
                  filesystem,
                  captureRules,
                  transitiveAggregatorRules);
            });
  }

  private ImmutableSet<CxxInferCaptureRulesAggregator> requireTransitiveCaptureAndAggregatingRules(
      CxxConstructorArg args, Flavor requiredFlavor) {
    ImmutableSet<BuildRule> deps = args.getCxxDeps().get(ruleResolver, cxxPlatform);

    return requireTransitiveDependentLibraries(
        cxxPlatform, deps, requiredFlavor, CxxInferCaptureRulesAggregator.class);
  }

  private ImmutableMap<String, CxxSource> collectSources(
      BuildTarget buildTarget, CxxConstructorArg args) {
    InferFlavors.checkNoInferFlavors(buildTarget.getFlavors());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);
    return CxxDescriptionEnhancer.parseCxxSources(
        buildTarget, ruleResolver, ruleFinder, pathResolver, cxxPlatform, args);
  }

  private <T extends BuildRule> ImmutableSet<T> requireTransitiveDependentLibraries(
      final CxxPlatform cxxPlatform,
      final Iterable<? extends BuildRule> deps,
      final Flavor requiredFlavor,
      final Class<T> ruleClass) {
    final ImmutableSet.Builder<T> depsBuilder = ImmutableSet.builder();
    new AbstractBreadthFirstTraversal<BuildRule>(deps) {
      @Override
      public Iterable<BuildRule> visit(BuildRule buildRule) {
        if (buildRule instanceof CxxLibrary) {
          CxxLibrary library = (CxxLibrary) buildRule;
          depsBuilder.add(
              (ruleClass.cast(library.requireBuildRule(requiredFlavor, cxxPlatform.getFlavor()))));
          return buildRule.getBuildDeps();
        }
        return ImmutableSet.of();
      }
    }.start();
    return depsBuilder.build();
  }

  private ImmutableList<CxxPreprocessorInput> computePreprocessorInputForCxxBinaryDescriptionArg(
      BuildTarget target,
      CellPathResolver cellRoots,
      CxxPlatform cxxPlatform,
      CxxBinaryDescription.CommonArg args,
      HeaderSymlinkTree headerSymlinkTree,
      Optional<SymlinkTree> sandboxTree) {
    ImmutableSet<BuildRule> deps = args.getCxxDeps().get(ruleResolver, cxxPlatform);
    return CxxDescriptionEnhancer.collectCxxPreprocessorInput(
        target,
        cxxPlatform,
        deps,
        ImmutableListMultimap.copyOf(
            Multimaps.transformValues(
                CxxFlags.getLanguageFlagsWithMacros(
                    args.getPreprocessorFlags(),
                    args.getPlatformPreprocessorFlags(),
                    args.getLangPreprocessorFlags(),
                    cxxPlatform),
                f ->
                    CxxDescriptionEnhancer.toStringWithMacrosArgs(
                        target, cellRoots, ruleResolver, cxxPlatform, f))),
        ImmutableList.of(headerSymlinkTree),
        args.getFrameworks(),
        CxxPreprocessables.getTransitiveCxxPreprocessorInput(
            cxxPlatform,
            RichStream.from(deps).filter(CxxPreprocessorDep.class::isInstance).toImmutableList()),
        args.getIncludeDirs(),
        sandboxTree,
        args.getRawHeaders());
  }

  private ImmutableSet<CxxInferCapture> requireInferCaptureBuildRules(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      ImmutableMap<String, CxxSource> sources,
      CxxConstructorArg args) {

    InferFlavors.checkNoInferFlavors(target.getFlavors());

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);

    ImmutableMap<Path, SourcePath> headers =
        CxxDescriptionEnhancer.parseHeaders(
            target, ruleResolver, ruleFinder, pathResolver, Optional.of(cxxPlatform), args);

    // Setup the header symlink tree and combine all the preprocessor input from this rule
    // and all dependencies.

    boolean shouldCreateHeadersSymlinks = true;
    if (args instanceof CxxLibraryDescription.CommonArg) {
      shouldCreateHeadersSymlinks =
          ((CxxLibraryDescription.CommonArg) args)
              .getXcodePrivateHeadersSymlinks()
              .orElse(cxxPlatform.getPrivateHeadersSymlinksEnabled());
    }
    HeaderSymlinkTree headerSymlinkTree =
        CxxDescriptionEnhancer.requireHeaderSymlinkTree(
            target,
            filesystem,
            ruleResolver,
            cxxPlatform,
            headers,
            HeaderVisibility.PRIVATE,
            shouldCreateHeadersSymlinks);
    Optional<SymlinkTree> sandboxTree = Optional.empty();
    if (cxxBuckConfig.sandboxSources()) {
      sandboxTree = CxxDescriptionEnhancer.createSandboxTree(target, ruleResolver, cxxPlatform);
    }

    ImmutableList<CxxPreprocessorInput> preprocessorInputs;

    if (args instanceof CxxBinaryDescription.CommonArg) {
      preprocessorInputs =
          computePreprocessorInputForCxxBinaryDescriptionArg(
              target,
              cellRoots,
              cxxPlatform,
              (CxxBinaryDescription.CommonArg) args,
              headerSymlinkTree,
              sandboxTree);
    } else if (args instanceof CxxLibraryDescription.CommonArg) {
      preprocessorInputs =
          CxxLibraryDescription.getPreprocessorInputsForBuildingLibrarySources(
              cxxBuckConfig,
              ruleResolver,
              cellRoots,
              target,
              (CxxLibraryDescription.CommonArg) args,
              cxxPlatform,
              args.getCxxDeps().get(ruleResolver, cxxPlatform),
              CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction.fromLibraryRule(),
              ImmutableList.of(headerSymlinkTree),
              sandboxTree);
    } else {
      throw new IllegalStateException("Only Binary and Library args supported.");
    }

    CxxSourceRuleFactory factory =
        CxxSourceRuleFactory.of(
            filesystem,
            target,
            ruleResolver,
            pathResolver,
            ruleFinder,
            cxxBuckConfig,
            cxxPlatform,
            preprocessorInputs,
            Multimaps.transformValues(
                CxxFlags.getLanguageFlagsWithMacros(
                    args.getCompilerFlags(),
                    args.getPlatformCompilerFlags(),
                    args.getLangCompilerFlags(),
                    cxxPlatform),
                f ->
                    CxxDescriptionEnhancer.toStringWithMacrosArgs(
                        target, cellRoots, ruleResolver, cxxPlatform, f)),
            args.getPrefixHeader(),
            args.getPrecompiledHeader(),
            PicType.PDC,
            sandboxTree);
    return factory.requireInferCaptureBuildRules(sources, inferBuckConfig);
  }
}
