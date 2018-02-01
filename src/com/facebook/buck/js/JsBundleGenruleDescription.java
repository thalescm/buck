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

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.android.toolchain.ndk.AndroidNdk;
import com.facebook.buck.apple.AppleBundleResources;
import com.facebook.buck.apple.HasAppleBundleResourcesDescription;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.sandbox.SandboxExecutionStrategy;
import com.facebook.buck.shell.AbstractGenruleDescription;
import com.facebook.buck.shell.ExportFile;
import com.facebook.buck.shell.ExportFileDescription;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.MoreSuppliers;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Supplier;
import org.immutables.value.Value;

public class JsBundleGenruleDescription
    extends AbstractGenruleDescription<JsBundleGenruleDescriptionArg>
    implements Flavored,
        HasAppleBundleResourcesDescription<JsBundleGenruleDescriptionArg>,
        JsBundleOutputsDescription<JsBundleGenruleDescriptionArg> {

  public JsBundleGenruleDescription(
      ToolchainProvider toolchainProvider, SandboxExecutionStrategy sandboxExecutionStrategy) {
    super(toolchainProvider, sandboxExecutionStrategy, false);
  }

  @Override
  public Class<JsBundleGenruleDescriptionArg> getConstructorArgType() {
    return JsBundleGenruleDescriptionArg.class;
  }

  @Override
  protected BuildRule createBuildRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      JsBundleGenruleDescriptionArg args,
      Optional<Arg> cmd,
      Optional<Arg> bash,
      Optional<Arg> cmdExe) {
    ImmutableSortedSet<Flavor> flavors = buildTarget.getFlavors();
    BuildTarget bundleTarget = args.getJsBundle().withAppendedFlavors(flavors);
    BuildRule jsBundle = resolver.requireRule(bundleTarget);

    if (flavors.contains(JsFlavors.SOURCE_MAP)
        || flavors.contains(JsFlavors.DEPENDENCY_FILE)
        || flavors.contains(JsFlavors.MISC)) {
      // SOURCE_MAP is a special flavor that allows accessing the written source map, typically
      // via export_file in reference mode
      // DEPENDENCY_FILE is a special flavor that triggers building a single file (format defined by
      // the worker)
      // MISC_DIR allows accessing the "misc" directory that can contain diverse assets not meant
      // to be part of the app being shipped.

      SourcePath output;
      if (args.getRewriteSourcemap() && flavors.contains(JsFlavors.SOURCE_MAP)) {
        output =
            ((JsBundleOutputs)
                    resolver.requireRule(buildTarget.withoutFlavors(JsFlavors.SOURCE_MAP)))
                .getSourcePathToSourceMap();
      } else if (args.getRewriteMisc() && flavors.contains(JsFlavors.MISC)) {
        output =
            ((JsBundleOutputs) resolver.requireRule(buildTarget.withoutFlavors(JsFlavors.MISC)))
                .getSourcePathToMisc();
      } else {
        output =
            Preconditions.checkNotNull(
                jsBundle.getSourcePathToOutput(), "%s has no output", jsBundle.getBuildTarget());
      }

      Path fileName =
          DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver))
              .getRelativePath(output)
              .getFileName();
      return new ExportFile(
          buildTarget,
          projectFilesystem,
          new SourcePathRuleFinder(resolver),
          fileName.toString(),
          ExportFileDescription.Mode.REFERENCE,
          output);
    }

    if (!(jsBundle instanceof JsBundleOutputs)) {
      throw new HumanReadableException(
          "The 'js_bundle' argument of %s, %s, must correspond to a js_bundle() rule.",
          buildTarget, bundleTarget);
    }

    Supplier<? extends SortedSet<BuildRule>> originalExtraDeps = params.getExtraDeps();
    return new JsBundleGenrule(
        buildTarget,
        projectFilesystem,
        sandboxExecutionStrategy,
        resolver,
        params.withExtraDeps(
            MoreSuppliers.memoize(
                () ->
                    ImmutableSortedSet.<BuildRule>naturalOrder()
                        .addAll(originalExtraDeps.get())
                        .add(jsBundle)
                        .build())),
        args,
        cmd,
        bash,
        cmdExe,
        (JsBundleOutputs) jsBundle,
        args.getEnvironmentExpansionSeparator(),
        toolchainProvider.getByNameIfPresent(
            AndroidPlatformTarget.DEFAULT_NAME, AndroidPlatformTarget.class),
        toolchainProvider.getByNameIfPresent(AndroidNdk.DEFAULT_NAME, AndroidNdk.class),
        toolchainProvider.getByNameIfPresent(
            AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class));
  }

  @Override
  public void addAppleBundleResources(
      AppleBundleResources.Builder builder,
      TargetNode<JsBundleGenruleDescriptionArg, ?> targetNode,
      ProjectFilesystem filesystem,
      BuildRuleResolver resolver) {
    if (!targetNode.getConstructorArg().getSkipResources()) {
      JsBundleGenrule genrule =
          resolver.getRuleWithType(targetNode.getBuildTarget(), JsBundleGenrule.class);
      JsBundleDescription.addAppleBundleResources(builder, genrule);
    }
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return JsBundleDescription.supportsFlavors(flavors);
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return Optional.of(JsBundleDescription.FLAVOR_DOMAINS);
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractJsBundleGenruleDescriptionArg extends AbstractGenruleDescription.CommonArg {
    BuildTarget getJsBundle();

    default String getOut() {
      return JsBundleOutputs.JS_DIR_NAME;
    }

    @Value.Default
    default boolean getRewriteSourcemap() {
      return false;
    }

    @Value.Default
    default boolean getRewriteMisc() {
      return false;
    }

    @Value.Default
    default boolean getSkipResources() {
      return false;
    }

    @Override
    default Optional<String> getType() {
      return Optional.of("js_bundle");
    }
  }
}
