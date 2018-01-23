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

package com.facebook.buck.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.Keystore;
import com.facebook.buck.jvm.java.KeystoreBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.parser.BuildTargetParser;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SingleThreadedBuildRuleResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TestBuildRuleParams;
import com.facebook.buck.rules.TestCellBuilder;
import com.facebook.buck.sandbox.NoSandboxExecutionStrategy;
import com.facebook.buck.shell.AbstractGenruleStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.step.fs.SymlinkTreeStep;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.toolchain.impl.ToolchainProviderBuilder;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.easymock.EasyMock;
import org.junit.Test;

public class ApkGenruleTest {

  private void createSampleAndroidBinaryRule(
      BuildRuleResolver ruleResolver, ProjectFilesystem filesystem)
      throws NoSuchBuildTargetException {
    // Create a java_binary that depends on a java_library so it is possible to create a
    // java_binary rule with a classpath entry and a main class.
    BuildTarget libAndroidTarget =
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//:lib-android");
    BuildRule androidLibRule =
        JavaLibraryBuilder.createBuilder(libAndroidTarget)
            .addSrc(Paths.get("java/com/facebook/util/Facebook.java"))
            .build(ruleResolver, filesystem);

    BuildTarget keystoreTarget =
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//keystore:debug");
    Keystore keystore =
        KeystoreBuilder.createBuilder(keystoreTarget)
            .setStore(FakeSourcePath.of(filesystem, "keystore/debug.keystore"))
            .setProperties(FakeSourcePath.of(filesystem, "keystore/debug.keystore.properties"))
            .build(ruleResolver, filesystem);

    AndroidBinaryBuilder.createBuilder(
            BuildTargetFactory.newInstance(filesystem.getRootPath(), "//:fb4a"))
        .setManifest(FakeSourcePath.of("AndroidManifest.xml"))
        .setOriginalDeps(ImmutableSortedSet.of(androidLibRule.getBuildTarget()))
        .setKeystore(keystore.getBuildTarget())
        .build(ruleResolver, filesystem);
  }

  @Test
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  public void testCreateAndRunApkGenrule()
      throws InterruptedException, IOException, NoSuchBuildTargetException {
    ProjectFilesystem projectFilesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
    FileSystem fileSystem = projectFilesystem.getRootPath().getFileSystem();
    BuildRuleResolver ruleResolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    createSampleAndroidBinaryRule(ruleResolver, projectFilesystem);

    // From the Python object, create a ApkGenruleBuildRuleFactory to create a ApkGenrule.Builder
    // that builds a ApkGenrule from the Python object.
    BuildTargetParser parser = EasyMock.createNiceMock(BuildTargetParser.class);
    final BuildTarget apkTarget =
        BuildTargetFactory.newInstance(projectFilesystem.getRootPath(), "//:fb4a");

    EasyMock.expect(
            parser.parse(
                EasyMock.eq(":fb4a"),
                EasyMock.anyObject(BuildTargetPatternParser.class),
                EasyMock.anyObject()))
        .andStubReturn(apkTarget);
    EasyMock.replay(parser);
    BuildTarget buildTarget =
        BuildTargetFactory.newInstance(
            projectFilesystem.getRootPath(), "//src/com/facebook:sign_fb4a");
    SourcePathResolver pathResolver =
        DefaultSourcePathResolver.from(new SourcePathRuleFinder(ruleResolver));

    ToolchainProvider toolchainProvider = new ToolchainProviderBuilder().build();

    ApkGenruleDescription description =
        new ApkGenruleDescription(toolchainProvider, new NoSandboxExecutionStrategy());
    ApkGenruleDescriptionArg arg =
        ApkGenruleDescriptionArg.builder()
            .setName(buildTarget.getShortName())
            .setApk(new FakeInstallable(apkTarget).getBuildTarget())
            .setBash("")
            .setCmd("python signer.py $APK key.properties > $OUT")
            .setCmdExe("")
            .setOut("signed_fb4a.apk")
            .setSrcs(
                ImmutableList.of(
                    PathSourcePath.of(
                        projectFilesystem, fileSystem.getPath("src/com/facebook/signer.py")),
                    PathSourcePath.of(
                        projectFilesystem, fileSystem.getPath("src/com/facebook/key.properties"))))
            .build();
    BuildRuleParams params = TestBuildRuleParams.create();
    ApkGenrule apkGenrule =
        (ApkGenrule)
            description.createBuildRule(
                TargetGraph.EMPTY,
                buildTarget,
                projectFilesystem,
                params,
                ruleResolver,
                TestCellBuilder.createCellRoots(projectFilesystem),
                arg);
    ruleResolver.addToIndex(apkGenrule);

    // Verify all of the observers of the Genrule.
    Path expectedApkOutput =
        projectFilesystem.resolve(
            projectFilesystem.getBuckPaths().getGenDir().toString()
                + "/src/com/facebook/sign_fb4a/sign_fb4a.apk");
    assertEquals(expectedApkOutput, apkGenrule.getAbsoluteOutputFilePath());
    assertEquals(
        "The apk that this rule is modifying must have the apk in its deps.",
        ImmutableSet.of(apkTarget.toString()),
        apkGenrule
            .getBuildDeps()
            .stream()
            .map(Object::toString)
            .collect(ImmutableSet.toImmutableSet()));
    BuildContext buildContext =
        FakeBuildContext.withSourcePathResolver(pathResolver)
            .withBuildCellRootPath(projectFilesystem.getRootPath());
    Iterable<Path> expectedInputsToCompareToOutputs =
        ImmutableList.of(
            fileSystem.getPath("src/com/facebook/signer.py"),
            fileSystem.getPath("src/com/facebook/key.properties"));
    MoreAsserts.assertIterablesEquals(
        expectedInputsToCompareToOutputs,
        pathResolver.filterInputsToCompareToOutput(apkGenrule.getSrcs()));

    // Verify that the shell commands that the genrule produces are correct.
    List<Step> steps = apkGenrule.getBuildSteps(buildContext, new FakeBuildableContext());
    MoreAsserts.assertStepsNames(
        "",
        ImmutableList.of("rm", "mkdir", "rm", "mkdir", "rm", "mkdir", "link_tree", "genrule"),
        steps);

    ExecutionContext executionContext = newEmptyExecutionContext();
    assertEquals(
        RmStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    buildContext.getBuildCellRootPath(),
                    projectFilesystem,
                    projectFilesystem
                        .getBuckPaths()
                        .getGenDir()
                        .resolve("src/com/facebook/sign_fb4a")))
            .withRecursive(true),
        steps.get(0));
    assertEquals(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(),
                projectFilesystem,
                projectFilesystem
                    .getBuckPaths()
                    .getGenDir()
                    .resolve("src/com/facebook/sign_fb4a"))),
        steps.get(1));

    assertEquals(
        RmStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    buildContext.getBuildCellRootPath(),
                    projectFilesystem,
                    projectFilesystem
                        .getBuckPaths()
                        .getGenDir()
                        .resolve("src/com/facebook/sign_fb4a__tmp")))
            .withRecursive(true),
        steps.get(2));
    assertEquals(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(),
                projectFilesystem,
                projectFilesystem
                    .getBuckPaths()
                    .getGenDir()
                    .resolve("src/com/facebook/sign_fb4a__tmp"))),
        steps.get(3));

    Path relativePathToSrcDir =
        projectFilesystem.getBuckPaths().getGenDir().resolve("src/com/facebook/sign_fb4a__srcs");
    assertEquals(
        RmStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    buildContext.getBuildCellRootPath(), projectFilesystem, relativePathToSrcDir))
            .withRecursive(true),
        steps.get(4));
    assertEquals(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(), projectFilesystem, relativePathToSrcDir)),
        steps.get(5));

    assertEquals(
        new SymlinkTreeStep(
            projectFilesystem,
            relativePathToSrcDir,
            ImmutableMap.of(
                projectFilesystem.getPath("signer.py"),
                projectFilesystem.getPath("src/com/facebook/signer.py"),
                projectFilesystem.getPath("key.properties"),
                projectFilesystem.getPath("src/com/facebook/key.properties"))),
        steps.get(6));

    Step genruleStep = steps.get(7);
    assertTrue(genruleStep instanceof AbstractGenruleStep);
    AbstractGenruleStep genruleCommand = (AbstractGenruleStep) genruleStep;
    assertEquals("genrule", genruleCommand.getShortName());
    ImmutableMap<String, String> environmentVariables =
        genruleCommand.getEnvironmentVariables(executionContext);
    assertEquals(
        new ImmutableMap.Builder<String, String>()
            .put(
                "APK",
                projectFilesystem
                    .resolve(BuildTargets.getGenPath(projectFilesystem, apkTarget, "%s.apk"))
                    .toString())
            .put("OUT", expectedApkOutput.toString())
            .build(),
        environmentVariables);

    Path scriptFilePath = genruleCommand.getScriptFilePath(executionContext);
    String scriptFileContents = genruleCommand.getScriptFileContents(executionContext);
    assertEquals(
        ImmutableList.of("/bin/bash", "-e", scriptFilePath.toString()),
        genruleCommand.getShellCommand(executionContext));
    assertEquals("python signer.py $APK key.properties > $OUT", scriptFileContents);

    EasyMock.verify(parser);
  }

  private ExecutionContext newEmptyExecutionContext() {
    return TestExecutionContext.newBuilder()
        .setPlatform(Platform.LINUX) // Fix platform to Linux to use bash in genrule.
        .build();
  }

  private static class FakeInstallable extends FakeBuildRule implements HasInstallableApk {

    public FakeInstallable(BuildTarget buildTarget) {
      super(buildTarget);
    }

    @Override
    public ApkInfo getApkInfo() {
      return ApkInfo.builder()
          .setApkPath(FakeSourcePath.of("buck-out/gen/fb4a.apk"))
          .setManifestPath(FakeSourcePath.of("spoof"))
          .build();
    }
  }
}
