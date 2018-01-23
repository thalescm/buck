/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.jvm.java;

import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkables;
import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.HasJavaAbi;
import com.facebook.buck.jvm.core.JavaLibrary;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Common utilities for working with {@link JavaLibrary} objects. */
public class JavaLibraryRules {

  /** Utility class: do not instantiate. */
  private JavaLibraryRules() {}

  static void addAccumulateClassNamesStep(
      BuildTarget target,
      ProjectFilesystem filesystem,
      @Nullable SourcePath sourcePathToOutput,
      BuildableContext buildableContext,
      BuildContext buildContext,
      ImmutableList.Builder<Step> steps) {

    Path pathToClassHashes = JavaLibraryRules.getPathToClassHashes(target, filesystem);
    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(), filesystem, pathToClassHashes.getParent())));
    steps.add(
        new AccumulateClassNamesStep(
            filesystem,
            Optional.ofNullable(sourcePathToOutput)
                .map(buildContext.getSourcePathResolver()::getRelativePath),
            pathToClassHashes));
    buildableContext.recordArtifact(pathToClassHashes);
  }

  static JavaLibrary.Data initializeFromDisk(BuildTarget buildTarget, ProjectFilesystem filesystem)
      throws IOException {
    List<String> lines = filesystem.readLines(getPathToClassHashes(buildTarget, filesystem));
    ImmutableSortedMap<String, HashCode> classHashes =
        AccumulateClassNamesStep.parseClassHashes(lines);

    return new JavaLibrary.Data(classHashes);
  }

  private static Path getPathToClassHashes(BuildTarget buildTarget, ProjectFilesystem filesystem) {
    return BuildTargets.getGenPath(filesystem, buildTarget, "%s.classes.txt");
  }

  /**
   * @return all the transitive native libraries a rule depends on, represented as a map from their
   *     system-specific library names to their {@link SourcePath} objects.
   */
  public static ImmutableMap<String, SourcePath> getNativeLibraries(
      Iterable<BuildRule> deps, final CxxPlatform cxxPlatform) {
    // Allow the transitive walk to find NativeLinkables through the BuildRuleParams deps of a
    // JavaLibrary or CalculateAbi object. The deps may be either one depending if we're compiling
    // against ABI rules or full rules
    return NativeLinkables.getTransitiveSharedLibraries(
        cxxPlatform,
        deps,
        r ->
            r instanceof JavaLibrary || r instanceof CalculateAbi
                ? Optional.of(r.getBuildDeps())
                : Optional.empty(),
        true);
  }

  public static ImmutableSortedSet<BuildRule> getAbiRules(
      BuildRuleResolver resolver, Iterable<BuildRule> inputs) {
    ImmutableSortedSet.Builder<BuildRule> abiRules = ImmutableSortedSet.naturalOrder();
    for (BuildRule input : inputs) {
      if (input instanceof HasJavaAbi && ((HasJavaAbi) input).getAbiJar().isPresent()) {
        Optional<BuildTarget> abiJarTarget = ((HasJavaAbi) input).getAbiJar();
        BuildRule abiJarRule = resolver.requireRule(abiJarTarget.get());
        abiRules.add(abiJarRule);
      }
    }
    return abiRules.build();
  }

  public static ImmutableSortedSet<BuildRule> getSourceOnlyAbiRules(
      BuildRuleResolver resolver, Iterable<BuildRule> inputs) {
    ImmutableSortedSet.Builder<BuildRule> abiRules = ImmutableSortedSet.naturalOrder();
    for (BuildRule input : inputs) {
      if (input instanceof HasJavaAbi) {
        HasJavaAbi hasAbi = (HasJavaAbi) input;
        Optional<BuildTarget> abiJarTarget = hasAbi.getSourceOnlyAbiJar();
        if (!abiJarTarget.isPresent()) {
          abiJarTarget = hasAbi.getAbiJar();
        }

        if (abiJarTarget.isPresent()) {
          BuildRule abiJarRule = resolver.requireRule(abiJarTarget.get());
          abiRules.add(abiJarRule);
        }
      }
    }
    return abiRules.build();
  }

  public static ZipArchiveDependencySupplier getAbiClasspath(
      BuildRuleResolver resolver, Iterable<BuildRule> inputs) {
    return new ZipArchiveDependencySupplier(
        new SourcePathRuleFinder(resolver),
        getAbiRules(resolver, inputs)
            .stream()
            .map(BuildRule::getSourcePathToOutput)
            .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural())));
  }

  /**
   * Iterates the input BuildRules and translates them to their ABI rules when possible. This is
   * necessary when constructing a BuildRuleParams object, for example, where we want to translate
   * rules to their ABI rules, but not skip over BuildRules such as GenAidl, CxxLibrary, NdkLibrary,
   * AndroidResource, etc. These should still be returned from this method, but without translation.
   */
  public static ImmutableSortedSet<BuildRule> getAbiRulesWherePossible(
      BuildRuleResolver resolver, Iterable<BuildRule> inputs) {
    ImmutableSortedSet.Builder<BuildRule> abiRules = ImmutableSortedSet.naturalOrder();
    for (BuildRule dep : inputs) {
      if (dep instanceof HasJavaAbi) {
        Optional<BuildTarget> abiJarTarget = ((HasJavaAbi) dep).getAbiJar();
        if (abiJarTarget.isPresent()) {
          abiRules.add(resolver.requireRule(abiJarTarget.get()));
        }
      } else {
        abiRules.add(dep);
      }
    }
    return abiRules.build();
  }
}
