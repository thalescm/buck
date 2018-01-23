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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.cxx.toolchain.CxxPlatformUtils;
import com.facebook.buck.cxx.toolchain.GccCompiler;
import com.facebook.buck.cxx.toolchain.GccPreprocessor;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultSourcePathResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.HashedFileTool;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SingleThreadedBuildRuleResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.RuleKeyAppendableFunction;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Test;

public class CxxCompilationDatabaseTest {

  @Test
  public void testCompilationDatabase() throws Exception {
    BuildTarget testBuildTarget =
        BuildTargetFactory.newInstance("//foo:baz")
            .withAppendedFlavors(ImmutableSet.of(CxxCompilationDatabase.COMPILATION_DATABASE));

    final String root = "/Users/user/src";
    final Path fakeRoot = Paths.get(root);
    ProjectFilesystem filesystem = new FakeProjectFilesystem(fakeRoot);

    BuildRuleResolver testBuildRuleResolver =
        new SingleThreadedBuildRuleResolver(
            TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(testBuildRuleResolver);
    SourcePathResolver testSourcePathResolver = DefaultSourcePathResolver.from(ruleFinder);

    HeaderSymlinkTree privateSymlinkTree =
        CxxDescriptionEnhancer.createHeaderSymlinkTree(
            testBuildTarget,
            filesystem,
            testBuildRuleResolver,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            ImmutableMap.of(),
            HeaderVisibility.PRIVATE,
            true);
    testBuildRuleResolver.addToIndex(privateSymlinkTree);
    HeaderSymlinkTree exportedSymlinkTree =
        CxxDescriptionEnhancer.createHeaderSymlinkTree(
            testBuildTarget,
            filesystem,
            testBuildRuleResolver,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            ImmutableMap.of(),
            HeaderVisibility.PUBLIC,
            true);
    testBuildRuleResolver.addToIndex(exportedSymlinkTree);

    BuildTarget compileTarget = testBuildTarget.withFlavors(InternalFlavor.of("compile-test.cpp"));

    PreprocessorFlags preprocessorFlags =
        PreprocessorFlags.builder()
            .addIncludes(
                CxxHeadersDir.of(
                    CxxPreprocessables.IncludeType.SYSTEM, FakeSourcePath.of("/foo/bar")),
                CxxHeadersDir.of(CxxPreprocessables.IncludeType.SYSTEM, FakeSourcePath.of("/test")))
            .build();

    ImmutableSortedSet.Builder<CxxPreprocessAndCompile> rules = ImmutableSortedSet.naturalOrder();
    class FrameworkPathAppendableFunction
        implements RuleKeyAppendableFunction<FrameworkPath, Path> {
      @Override
      public void appendToRuleKey(RuleKeyObjectSink sink) {
        // Do nothing.
      }

      @Override
      public Path apply(FrameworkPath input) {
        throw new UnsupportedOperationException("should not be called");
      }
    }
    rules.add(
        testBuildRuleResolver.addToIndex(
            CxxPreprocessAndCompile.preprocessAndCompile(
                compileTarget,
                filesystem,
                ImmutableSortedSet.of(privateSymlinkTree, exportedSymlinkTree),
                new PreprocessorDelegate(
                    testSourcePathResolver,
                    CxxPlatformUtils.DEFAULT_COMPILER_DEBUG_PATH_SANITIZER,
                    CxxPlatformUtils.DEFAULT_PLATFORM.getHeaderVerification(),
                    filesystem.getRootPath(),
                    new GccPreprocessor(
                        new HashedFileTool(
                            PathSourcePath.of(filesystem, Paths.get("preprocessor")))),
                    preprocessorFlags,
                    new FrameworkPathAppendableFunction(),
                    Optional.empty(),
                    /* leadingIncludePaths */ Optional.empty()),
                new CompilerDelegate(
                    CxxPlatformUtils.DEFAULT_COMPILER_DEBUG_PATH_SANITIZER,
                    new GccCompiler(
                        new HashedFileTool(PathSourcePath.of(filesystem, Paths.get("compiler")))),
                    CxxToolFlags.of()),
                Paths.get("test.o"),
                FakeSourcePath.of(filesystem, "test.cpp"),
                CxxSource.Type.CXX,
                Optional.empty(),
                CxxPlatformUtils.DEFAULT_COMPILER_DEBUG_PATH_SANITIZER,
                Optional.empty())));

    CxxCompilationDatabase compilationDatabase =
        CxxCompilationDatabase.createCompilationDatabase(
            testBuildTarget, filesystem, rules.build());
    testBuildRuleResolver.addToIndex(compilationDatabase);

    assertThat(
        compilationDatabase.getRuntimeDeps(ruleFinder).collect(ImmutableSet.toImmutableSet()),
        Matchers.contains(
            exportedSymlinkTree.getBuildTarget(), privateSymlinkTree.getBuildTarget()));

    assertEquals(
        "getPathToOutput() should be a function of the build target.",
        BuildTargets.getGenPath(filesystem, testBuildTarget, "__%s/compile_commands.json"),
        testSourcePathResolver.getRelativePath(compilationDatabase.getSourcePathToOutput()));

    List<Step> buildSteps =
        compilationDatabase.getBuildSteps(
            FakeBuildContext.withSourcePathResolver(testSourcePathResolver),
            new FakeBuildableContext());
    assertEquals(2, buildSteps.size());
    assertTrue(buildSteps.get(0) instanceof MkdirStep);
    assertTrue(buildSteps.get(1) instanceof CxxCompilationDatabase.GenerateCompilationCommandsJson);

    CxxCompilationDatabase.GenerateCompilationCommandsJson step =
        (CxxCompilationDatabase.GenerateCompilationCommandsJson) buildSteps.get(1);
    Iterable<CxxCompilationDatabaseEntry> observedEntries =
        step.createEntries().collect(Collectors.toList());
    Iterable<CxxCompilationDatabaseEntry> expectedEntries =
        ImmutableList.of(
            CxxCompilationDatabaseEntry.of(
                root,
                root + "/test.cpp",
                ImmutableList.of(
                    "/Users/user/src/compiler",
                    "-x",
                    "c++",
                    "-isystem",
                    "/foo/bar",
                    "-isystem",
                    "/test",
                    "-c",
                    "-MD",
                    "-MF",
                    "test.o.dep",
                    "test.cpp",
                    "-o",
                    "test.o")));
    MoreAsserts.assertIterablesEquals(expectedEntries, observedEntries);
  }
}
