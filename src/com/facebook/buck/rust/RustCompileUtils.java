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

package com.facebook.buck.rust;

import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxGenruleDescription;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.cxx.toolchain.linker.Linkers;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkables;
import com.facebook.buck.graph.AbstractBreadthFirstTraversal;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BinaryWrapperRule;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.ForwardingBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SymlinkTree;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.types.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Stream;

/** Utilities to generate various kinds of Rust compilation. */
public class RustCompileUtils {
  private RustCompileUtils() {}

  protected static BuildTarget getCompileBuildTarget(
      BuildTarget target, CxxPlatform cxxPlatform, CrateType crateType) {
    return target.withFlavors(cxxPlatform.getFlavor(), crateType.getFlavor());
  }

  // Construct a RustCompileRule with:
  // - all sources
  // - rustc
  // - linker
  // - rustc optim / feature / cfg / user-specified flags
  // - linker args
  // - `--extern <crate>=<rlibpath>` for direct dependencies
  // - `-L dependency=<dir>` for transitive dependencies
  // - `-C relocation-model=pic/static/default/dynamic-no-pic` according to flavor
  // - `--emit metadata` if flavor is "check"
  // - `--crate-type lib/rlib/dylib/cdylib/staticlib` according to flavor
  private static RustCompileRule createBuild(
      BuildTarget target,
      String crateName,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathRuleFinder ruleFinder,
      CxxPlatform cxxPlatform,
      RustBuckConfig rustConfig,
      ImmutableList<String> extraFlags,
      ImmutableList<String> extraLinkerFlags,
      Iterable<Arg> linkerInputs,
      CrateType crateType,
      Linker.LinkableDepType depType,
      boolean rpath,
      ImmutableSortedSet<SourcePath> sources,
      SourcePath rootModule) {
    SortedSet<BuildRule> ruledeps = params.getBuildDeps();
    ImmutableList.Builder<Arg> linkerArgs = ImmutableList.builder();

    Stream.concat(rustConfig.getLinkerArgs(cxxPlatform).stream(), extraLinkerFlags.stream())
        .map(StringArg::of)
        .forEach(linkerArgs::add);

    linkerArgs.addAll(linkerInputs);

    ImmutableList.Builder<Arg> args = ImmutableList.builder();
    ImmutableList.Builder<Arg> depArgs = ImmutableList.builder();

    String relocModel;
    if (crateType.isPic()) {
      relocModel = "pic";
    } else {
      relocModel = "static";
    }

    Stream<String> checkArgs;
    if (crateType.isCheck()) {
      args.add(StringArg.of("--emit=metadata"));
      checkArgs = rustConfig.getRustCheckFlags().stream();
    } else {
      checkArgs = Stream.of();
    }

    Stream.of(
            Stream.of(
                String.format("--crate-name=%s", crateName),
                String.format("--crate-type=%s", crateType),
                String.format("-Crelocation-model=%s", relocModel)),
            extraFlags.stream(),
            checkArgs)
        .flatMap(x -> x)
        .map(StringArg::of)
        .forEach(args::add);

    // Find direct and transitive Rust deps. We do this in two passes, since a dependency that's
    // both direct and transitive needs to be listed on the command line in each form.
    //
    // This could end up with a lot of redundant parameters (lots of rlibs in one directory),
    // but Arg isn't comparable, so we can't put it in a Set.

    // First pass - direct deps
    ruledeps
        .stream()
        .filter(RustLinkable.class::isInstance)
        .map(
            rule ->
                ((RustLinkable) rule).getLinkerArg(true, crateType.isCheck(), cxxPlatform, depType))
        .forEach(depArgs::add);

    // Second pass - indirect deps
    new AbstractBreadthFirstTraversal<BuildRule>(
        ruledeps
            .stream()
            .flatMap(r -> r.getBuildDeps().stream())
            .collect(ImmutableList.toImmutableList())) {
      @Override
      public Iterable<BuildRule> visit(BuildRule rule) {
        SortedSet<BuildRule> deps = ImmutableSortedSet.of();
        if (rule instanceof RustLinkable) {
          deps = rule.getBuildDeps();

          Arg arg =
              ((RustLinkable) rule).getLinkerArg(false, crateType.isCheck(), cxxPlatform, depType);

          depArgs.add(arg);
        }
        return deps;
      }
    }.start();

    // A native crate output is no longer intended for consumption by the Rust toolchain;
    // it's either an executable, or a native library that C/C++ can link with. Rust DYLIBs
    // also need all dependencies available.
    if (crateType.needAllDeps()) {
      ImmutableList<Arg> nativeArgs =
          NativeLinkables.getTransitiveNativeLinkableInput(
                  cxxPlatform,
                  ruledeps,
                  depType,
                  r -> r instanceof RustLinkable ? Optional.of(r.getBuildDeps()) : Optional.empty())
              .getArgs();

      // Add necessary rpaths if we're dynamically linking with things
      if (rpath && depType == Linker.LinkableDepType.SHARED) {
        args.add(StringArg.of("-Crpath"));
      }

      linkerArgs.addAll(nativeArgs);
    }

    // If we want shared deps or are building a dynamic rlib, make sure we prefer
    // dynamic dependencies (esp to get dynamic dependency on standard libs)
    if (depType == Linker.LinkableDepType.SHARED || crateType == CrateType.DYLIB) {
      args.add(StringArg.of("-Cprefer-dynamic"));
    }

    String filename = crateType.filenameFor(target, crateName, cxxPlatform);

    return RustCompileRule.from(
        ruleFinder,
        target,
        projectFilesystem,
        params,
        filename,
        rustConfig.getRustCompiler().resolve(resolver),
        rustConfig.getLinkerProvider(cxxPlatform, cxxPlatform.getLd().getType()).resolve(resolver),
        args.build(),
        depArgs.build(),
        linkerArgs.build(),
        CxxGenruleDescription.fixupSourcePaths(resolver, ruleFinder, cxxPlatform, sources),
        CxxGenruleDescription.fixupSourcePath(resolver, ruleFinder, cxxPlatform, rootModule),
        crateType.hasOutput(),
        rustConfig.getRemapSrcPaths());
  }

  public static RustCompileRule requireBuild(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathRuleFinder ruleFinder,
      CxxPlatform cxxPlatform,
      RustBuckConfig rustConfig,
      ImmutableList<String> extraFlags,
      ImmutableList<String> extraLinkerFlags,
      Iterable<Arg> linkerInputs,
      String crateName,
      CrateType crateType,
      Linker.LinkableDepType depType,
      ImmutableSortedSet<SourcePath> sources,
      SourcePath rootModule) {
    return (RustCompileRule)
        resolver.computeIfAbsent(
            getCompileBuildTarget(buildTarget, cxxPlatform, crateType),
            target ->
                createBuild(
                    target,
                    crateName,
                    projectFilesystem,
                    params,
                    resolver,
                    ruleFinder,
                    cxxPlatform,
                    rustConfig,
                    extraFlags,
                    extraLinkerFlags,
                    linkerInputs,
                    crateType,
                    depType,
                    true,
                    sources,
                    rootModule));
  }

  public static Linker.LinkableDepType getLinkStyle(
      BuildTarget target, Optional<Linker.LinkableDepType> linkStyle) {
    Optional<RustBinaryDescription.Type> type = RustBinaryDescription.BINARY_TYPE.getValue(target);
    Linker.LinkableDepType ret;

    if (type.isPresent()) {
      ret = type.get().getLinkStyle();
    } else if (linkStyle.isPresent()) {
      ret = linkStyle.get();
    } else {
      ret = Linker.LinkableDepType.STATIC;
    }

    // XXX rustc always links executables with "-pie", which requires all objects to be built
    // with a PIC relocation model (-fPIC or -Crelocation-model=pic). Rust code does this by
    // default, but we need to make sure any C/C++ dependencies are also PIC.
    // So for now, remap STATIC -> STATIC_PIC, until we can control rustc's use of -pie.
    if (ret == Linker.LinkableDepType.STATIC) {
      ret = Linker.LinkableDepType.STATIC_PIC;
    }

    return ret;
  }

  public static BinaryWrapperRule createBinaryBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      RustBuckConfig rustBuckConfig,
      FlavorDomain<CxxPlatform> cxxPlatforms,
      CxxPlatform defaultCxxPlatform,
      Optional<String> crateName,
      ImmutableSortedSet<String> features,
      Iterator<String> rustcFlags,
      Iterator<String> linkerFlags,
      Linker.LinkableDepType linkStyle,
      boolean rpath,
      ImmutableSortedSet<SourcePath> srcs,
      Optional<SourcePath> crateRoot,
      ImmutableSet<String> defaultRoots,
      boolean isCheck) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);

    ImmutableList.Builder<String> rustcArgs = ImmutableList.builder();

    RustCompileUtils.addFeatures(buildTarget, features, rustcArgs);

    rustcArgs.addAll(rustcFlags);

    ImmutableList.Builder<String> linkerArgs = ImmutableList.builder();
    linkerArgs.addAll(linkerFlags);

    String crate = crateName.orElse(ruleToCrateName(buildTarget.getShortName()));

    CxxPlatform cxxPlatform = cxxPlatforms.getValue(buildTarget).orElse(defaultCxxPlatform);

    Pair<SourcePath, ImmutableSortedSet<SourcePath>> rootModuleAndSources =
        getRootModuleAndSources(
            buildTarget,
            resolver,
            pathResolver,
            ruleFinder,
            cxxPlatform,
            crate,
            crateRoot,
            defaultRoots,
            srcs);

    // The target to use for the link rule.
    BuildTarget binaryTarget =
        buildTarget.withAppendedFlavors(
            isCheck ? RustDescriptionEnhancer.RFCHECK : RustDescriptionEnhancer.RFBIN);

    if (isCheck || !rustBuckConfig.getUnflavoredBinaries()) {
      binaryTarget = binaryTarget.withAppendedFlavors(cxxPlatform.getFlavor());
    }

    CommandTool.Builder executableBuilder = new CommandTool.Builder();

    // Special handling for dynamically linked binaries.
    if (linkStyle == Linker.LinkableDepType.SHARED) {

      // Create a symlink tree with for all native shared (NativeLinkable) libraries
      // needed by this binary.
      SymlinkTree sharedLibraries =
          resolver.addToIndex(
              CxxDescriptionEnhancer.createSharedLibrarySymlinkTree(
                  buildTarget,
                  projectFilesystem,
                  cxxPlatform,
                  params.getBuildDeps(),
                  r ->
                      r instanceof RustLinkable
                          ? Optional.of(r.getBuildDeps())
                          : Optional.empty()));

      // Embed a origin-relative library path into the binary so it can find the shared libraries.
      // The shared libraries root is absolute. Also need an absolute path to the linkOutput
      Path absBinaryDir =
          buildTarget
              .getCellPath()
              .resolve(RustCompileRule.getOutputDir(binaryTarget, projectFilesystem));

      linkerArgs.addAll(
          Linkers.iXlinker(
              "-rpath",
              String.format(
                  "%s/%s",
                  cxxPlatform.getLd().resolve(resolver).origin(),
                  absBinaryDir.relativize(sharedLibraries.getRoot()).toString())));

      // Add all the shared libraries and the symlink tree as inputs to the tool that represents
      // this binary, so that users can attach the proper deps.
      executableBuilder.addNonHashableInput(sharedLibraries.getRootSourcePath());
      executableBuilder.addInputs(sharedLibraries.getLinks().values());

      // Also add Rust shared libraries as runtime deps. We don't need these in the symlink tree
      // because rustc will include their dirs in rpath by default.
      Map<String, SourcePath> rustSharedLibraries =
          getTransitiveRustSharedLibraries(cxxPlatform, params.getBuildDeps());
      executableBuilder.addInputs(rustSharedLibraries.values());
    }

    final RustCompileRule buildRule =
        (RustCompileRule)
            resolver.computeIfAbsent(
                binaryTarget,
                binaryTarget1 ->
                    createBuild(
                        binaryTarget1,
                        crate,
                        projectFilesystem,
                        params,
                        resolver,
                        ruleFinder,
                        cxxPlatform,
                        rustBuckConfig,
                        rustcArgs.build(),
                        linkerArgs.build(),
                        /* linkerInputs */ ImmutableList.of(),
                        isCheck ? CrateType.CHECKBIN : CrateType.BIN,
                        linkStyle,
                        rpath,
                        rootModuleAndSources.getSecond(),
                        rootModuleAndSources.getFirst()));

    // Add the binary as the first argument.
    executableBuilder.addArg(SourcePathArg.of(buildRule.getSourcePathToOutput()));

    final CommandTool executable = executableBuilder.build();

    return new BinaryWrapperRule(
        buildTarget, projectFilesystem, params.copyAppendingExtraDeps(buildRule)) {

      @Override
      public Tool getExecutableCommand() {
        return executable;
      }

      @Override
      public SourcePath getSourcePathToOutput() {
        return ForwardingBuildTargetSourcePath.of(
            getBuildTarget(), buildRule.getSourcePathToOutput());
      }
    };
  }

  /**
   * Given a list of sources, return the one which is the root based on the defaults and user
   * parameters.
   *
   * @param resolver Source path resolver for rule
   * @param crate Name of crate
   * @param defaults Default names for this rule (library, binary, etc)
   * @param sources List of sources
   * @return The matching source
   */
  public static Optional<SourcePath> getCrateRoot(
      SourcePathResolver resolver,
      String crate,
      ImmutableSet<String> defaults,
      Stream<SourcePath> sources) {
    String crateName = String.format("%s.rs", crate);
    ImmutableList<SourcePath> res =
        sources
            .filter(
                src -> {
                  String name = resolver.getRelativePath(src).getFileName().toString();
                  return defaults.contains(name) || name.equals(crateName);
                })
            .collect(ImmutableList.toImmutableList());

    if (res.size() == 1) {
      return Optional.of(res.get(0));
    } else {
      return Optional.empty();
    }
  }

  public static void addFeatures(
      BuildTarget buildTarget, Iterable<String> features, ImmutableList.Builder<String> args) {
    for (String feature : features) {
      if (feature.contains("\"")) {
        throw new HumanReadableException(
            "%s contains an invalid feature name %s", buildTarget.getFullyQualifiedName(), feature);
      }

      args.add("--cfg", String.format("feature=\"%s\"", feature));
    }
  }

  public static String ruleToCrateName(String rulename) {
    return rulename.replace('-', '_');
  }

  /**
   * Collect all the shared libraries generated by {@link RustLinkable}s found by transitively
   * traversing all unbroken dependency chains of {@link com.facebook.buck.rust.RustLinkable}
   * objects found via the passed in {@link com.facebook.buck.rules.BuildRule} roots.
   *
   * @return a mapping of library name to the library {@link SourcePath}.
   */
  public static Map<String, SourcePath> getTransitiveRustSharedLibraries(
      CxxPlatform cxxPlatform, Iterable<? extends BuildRule> inputs) {
    ImmutableSortedMap.Builder<String, SourcePath> libs = ImmutableSortedMap.naturalOrder();

    new AbstractBreadthFirstTraversal<BuildRule>(inputs) {
      @Override
      public Iterable<BuildRule> visit(BuildRule rule) {
        Set<BuildRule> deps = ImmutableSet.of();
        if (rule instanceof RustLinkable) {
          RustLinkable rustLinkable = (RustLinkable) rule;

          if (!rustLinkable.isProcMacro()) {
            deps = rule.getBuildDeps();

            if (rustLinkable.getPreferredLinkage() != NativeLinkable.Linkage.STATIC) {
              libs.putAll(rustLinkable.getRustSharedLibraries(cxxPlatform));
            }
          }
        }
        return deps;
      }
    }.start();

    return libs.build();
  }

  static Pair<SourcePath, ImmutableSortedSet<SourcePath>> getRootModuleAndSources(
      BuildTarget target,
      BuildRuleResolver resolver,
      SourcePathResolver pathResolver,
      SourcePathRuleFinder ruleFinder,
      CxxPlatform cxxPlatform,
      String crate,
      Optional<SourcePath> crateRoot,
      ImmutableSet<String> defaultRoots,
      ImmutableSortedSet<SourcePath> srcs) {

    ImmutableSortedSet<SourcePath> fixedSrcs =
        CxxGenruleDescription.fixupSourcePaths(resolver, ruleFinder, cxxPlatform, srcs);

    Optional<SourcePath> rootModule =
        crateRoot
            .map(Optional::of)
            .orElse(getCrateRoot(pathResolver, crate, defaultRoots, fixedSrcs.stream()));

    return new Pair<>(
        rootModule.orElseThrow(
            () ->
                new HumanReadableException(
                    "Can't find suitable top-level source file for %s: %s",
                    target.getFullyQualifiedName(), fixedSrcs)),
        fixedSrcs);
  }

  /**
   * Approximate what Cargo does - it computes a hash based on the crate version and its
   * dependencies. Buck will deal with the dependencies and we don't need to worry about the
   * version, but we do need to make sure that two crates with the same name in the build are
   * distinct - so compute the hash from the full target path.
   *
   * @param target Which target we're computing the hash for
   * @return Truncated MD5 hash of the target path
   */
  static String hashForTarget(BuildTarget target) {
    String name = target.getUnflavoredBuildTarget().getFullyQualifiedName();
    Hasher hasher = Hashing.md5().newHasher();
    HashCode hash = hasher.putString(name, StandardCharsets.UTF_8).hash();
    return hash.toString().substring(0, 16);
  }
}
