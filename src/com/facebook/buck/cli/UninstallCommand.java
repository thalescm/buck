/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.cli;

import com.facebook.buck.android.AdbHelper;
import com.facebook.buck.android.HasInstallableApk;
import com.facebook.buck.android.exopackage.AndroidDevicesHelper;
import com.facebook.buck.android.exopackage.AndroidDevicesHelperFactory;
import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraphAndBuildTargets;
import com.facebook.buck.step.AdbOptions;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TargetDeviceOptions;
import com.facebook.buck.util.CloseableMemoizedSupplier;
import com.facebook.buck.util.CommandLineException;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.MoreExceptions;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class UninstallCommand extends AbstractCommand {

  public static class UninstallOptions {
    @VisibleForTesting static final String KEEP_LONG_ARG = "--keep";
    @VisibleForTesting static final String KEEP_SHORT_ARG = "-k";

    @Option(
      name = KEEP_LONG_ARG,
      aliases = {KEEP_SHORT_ARG},
      usage = "Keep user data when uninstalling."
    )
    private boolean keepData = false;

    public boolean shouldKeepUserData() {
      return keepData;
    }
  }

  @AdditionalOptions @SuppressFieldNotInitialized private UninstallOptions uninstallOptions;

  @AdditionalOptions @SuppressFieldNotInitialized private AdbCommandLineOptions adbOptions;

  @AdditionalOptions @SuppressFieldNotInitialized
  private TargetDeviceCommandLineOptions deviceOptions;

  @Argument private List<String> arguments = new ArrayList<>();

  public List<String> getArguments() {
    return arguments;
  }

  public UninstallOptions uninstallOptions() {
    return uninstallOptions;
  }

  public AdbOptions adbOptions(BuckConfig buckConfig) {
    return adbOptions.getAdbOptions(buckConfig);
  }

  public TargetDeviceOptions targetDeviceOptions() {
    return deviceOptions.getTargetDeviceOptions();
  }

  @Override
  public ExitCode runWithoutHelp(CommandRunnerParams params)
      throws IOException, InterruptedException {

    // Parse all of the build targets specified by the user.
    BuildRuleResolver resolver;
    ImmutableSet<BuildTarget> buildTargets;

    try (CommandThreadManager pool =
            new CommandThreadManager("Uninstall", getConcurrencyLimit(params.getBuckConfig()));
        CloseableMemoizedSupplier<ForkJoinPool, RuntimeException> poolSupplier =
            getForkJoinPoolSupplier(params.getBuckConfig())) {
      TargetGraphAndBuildTargets result =
          params
              .getParser()
              .buildTargetGraphForTargetNodeSpecs(
                  params.getBuckEventBus(),
                  params.getCell(),
                  getEnableParserProfiling(),
                  pool.getListeningExecutorService(),
                  parseArgumentsAsTargetNodeSpecs(params.getBuckConfig(), getArguments()));
      buildTargets = result.getBuildTargets();
      resolver =
          params
              .getActionGraphCache()
              .getActionGraph(
                  params.getBuckEventBus(),
                  result.getTargetGraph(),
                  params.getBuckConfig(),
                  params.getRuleKeyConfiguration(),
                  poolSupplier)
              .getResolver();
    } catch (BuildFileParseException e) {
      params
          .getBuckEventBus()
          .post(ConsoleEvent.severe(MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return ExitCode.PARSE_ERROR;
    }

    // Make sure that only one build target is specified.
    if (buildTargets.size() != 1) {
      throw new CommandLineException("must specify exactly one android_binary() rule");
    }
    BuildTarget buildTarget = Iterables.get(buildTargets, 0);

    // Find the android_binary() rule from the parse.
    BuildRule buildRule = resolver.requireRule(buildTarget);
    if (!(buildRule instanceof HasInstallableApk)) {
      params
          .getBuckEventBus()
          .post(
              ConsoleEvent.severe(
                  String.format(
                      "Specified rule %s must be of type android_binary() or apk_genrule() but was %s().\n",
                      buildRule.getFullyQualifiedName(), buildRule.getType())));
      return ExitCode.BUILD_ERROR;
    }
    HasInstallableApk hasInstallableApk = (HasInstallableApk) buildRule;

    final AndroidDevicesHelper adbHelper = getExecutionContext().getAndroidDevicesHelper().get();

    // Find application package name from manifest and uninstall from matching devices.
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(resolver));
    String appId =
        AdbHelper.tryToExtractPackageNameFromManifest(pathResolver, hasInstallableApk.getApkInfo());
    return adbHelper.uninstallApp(appId, uninstallOptions().shouldKeepUserData())
        ? ExitCode.SUCCESS
        : ExitCode.RUN_ERROR;
  }

  @Override
  protected ExecutionContext.Builder getExecutionContextBuilder(CommandRunnerParams params) {
    return super.getExecutionContextBuilder(params)
        .setAndroidDevicesHelper(
            AndroidDevicesHelperFactory.get(
                params.getCell().getToolchainProvider(),
                this::getExecutionContext,
                params.getBuckConfig(),
                adbOptions(params.getBuckConfig()),
                targetDeviceOptions()));
  }

  @Override
  public String getShortDescription() {
    return "uninstalls an APK";
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }
}
