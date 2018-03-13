/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.jvm.kotlin;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.CompileToJarStepFactory;
import com.facebook.buck.jvm.java.CompilerParameters;
import com.facebook.buck.jvm.java.ExtraClasspathProvider;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacToJarStepFactory;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.AddsToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

public class KotlincToJarStepFactory extends CompileToJarStepFactory implements AddsToRuleKey {

  @AddToRuleKey private final Kotlinc kotlinc;
  @AddToRuleKey private final ImmutableList<String> extraArguments;
  @AddToRuleKey private final ExtraClasspathProvider extraClassPath;

  private final ImmutableSortedSet<Path> kotlinHomeLibraries;
  private final Javac javac;
  private final JavacOptions javacOptions;

  public KotlincToJarStepFactory(
      SourcePathResolver resolver,
      SourcePathRuleFinder ruleFinder,
      ProjectFilesystem projectFilesystem,
      Kotlinc kotlinc,
      ImmutableSortedSet<Path> kotlinHomeLibraries,
      ImmutableList<String> extraArguments,
      ExtraClasspathProvider extraClassPath,
      Javac javac,
      JavacOptions javacOptions) {
    super(resolver, ruleFinder, projectFilesystem);
    this.kotlinc = kotlinc;
    this.kotlinHomeLibraries = kotlinHomeLibraries;
    this.extraArguments = extraArguments;
    this.extraClassPath = extraClassPath;
    this.javac = javac;
    this.javacOptions = Preconditions.checkNotNull(javacOptions);
  }

  @Override
  public void createCompileStep(
      BuildContext buildContext,
      BuildTarget invokingRule,
      CompilerParameters parameters,
      /* output params */
      Builder<Step> steps,
      BuildableContext buildableContext) {

    ImmutableSortedSet<Path> declaredClasspathEntries = parameters.getClasspathEntries();
    ImmutableSortedSet<Path> sourceFilePaths = parameters.getSourceFilePaths();
    Path outputDirectory = parameters.getOutputDirectory();
    Path pathToSrcsList = parameters.getPathToSourcesList();

    if (sourceFilePaths.stream().anyMatch(PathMatchers.KOTLIN_PATH_MATCHER::matches)) {
      ImmutableSortedSet<Path> allClasspaths = ImmutableSortedSet.<Path>naturalOrder()
          .addAll(
              Optional.ofNullable(extraClassPath.getExtraClasspath())
                  .orElse(ImmutableList.of()))
          .addAll(declaredClasspathEntries)
          .addAll(kotlinHomeLibraries)
          .build();

      steps.add(
          new KotlincStep(
              invokingRule,
              outputDirectory,
              sourceFilePaths,
              pathToSrcsList,
              allClasspaths,
              kotlinc,
              extraArguments,
              projectFilesystem,
              Optional.of(parameters.getWorkingDirectory())));
    }


    ImmutableSortedSet<Path> javaSourceFiles =
        ImmutableSortedSet.copyOf(
            sourceFilePaths
                .stream()
                .filter(input -> !PathMatchers.KOTLIN_PATH_MATCHER.matches(input))
                .collect(Collectors.toSet()));

    // Only invoke javac if we have java files.
    if (!javaSourceFiles.isEmpty()) {
      CompilerParameters javacParameters =
          CompilerParameters.builder()
              .from(parameters)
              .setClasspathEntries(
                  ImmutableSortedSet.<Path>naturalOrder()
                      .add(projectFilesystem.resolve(outputDirectory))
                      .addAll(
                          Optional.ofNullable(extraClassPath.getExtraClasspath())
                              .orElse(ImmutableList.of()))
                      .addAll(declaredClasspathEntries)
                      .build())
              .setSourceFilePaths(javaSourceFiles)
              .build();
      new JavacToJarStepFactory(
          resolver, ruleFinder, projectFilesystem, javac, javacOptions, extraClassPath)
          .createCompileStep(buildContext, invokingRule, javacParameters, steps, buildableContext);
    }
  }

  @Override
  protected Optional<String> getBootClasspath(BuildContext context) {
    return javacOptions.withBootclasspathFromContext(extraClassPath).getBootclasspath();
  }

  @Override
  public Tool getCompiler() {
    return kotlinc;
  }
}
