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

package com.facebook.buck.dotnet;

import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.util.Escaper;
import com.facebook.buck.util.types.Either;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class CsharpLibraryCompile extends ShellStep {
  private static final Path CSC = Paths.get("csc");
  private final Path output;
  private final ImmutableSortedSet<Path> srcs;
  private final ImmutableList<Either<Path, String>> references;
  private final FrameworkVersion version;
  private final ImmutableListMultimap<Path, String> resources;

  public CsharpLibraryCompile(
      BuildTarget buildTarget,
      Path output,
      ImmutableSortedSet<Path> srcs,
      ImmutableList<Either<Path, String>> references,
      ImmutableListMultimap<Path, String> resources,
      FrameworkVersion version) {
    super(Optional.of(buildTarget), output.getParent());
    this.references = references;
    this.resources = resources;
    this.version = version;
    Preconditions.checkState(output.isAbsolute());

    this.output = output;
    this.srcs = srcs;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    Path csc = new ExecutableFinder().getExecutable(CSC, context.getEnvironment());
    DotnetFramework netFramework =
        DotnetFramework.resolveFramework(context.getEnvironment(), version);

    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add(csc.toAbsolutePath().toString())
        .add("/target:library")
        .add("/out:" + output.toString());

    for (Either<Path, String> ref : references) {
      args.add("/reference:" + resolveReference(netFramework, ref));
    }

    for (Path resource : resources.keySet()) {
      for (String name : resources.get(resource)) {
        args.add("/resource:" + Escaper.escapeAsShellString(resource.toString()) + "," + name);
      }
    }

    args.addAll(
        srcs.stream()
            .map(input -> Escaper.escapeAsShellString(input.toAbsolutePath().toString()))
            .collect(ImmutableSet.toImmutableSet()));

    return args.build();
  }

  private String resolveReference(DotnetFramework netFramework, Either<Path, String> ref) {
    if (ref.isLeft()) {
      return ref.getLeft().toString();
    }

    Path pathToAssembly = netFramework.findReferenceAssembly(ref.getRight());
    return pathToAssembly.toString();
  }

  @Override
  public String getShortName() {
    return "compile csharp";
  }
}
