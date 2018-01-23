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

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

public class ZipalignStep extends ShellStep {

  private final AndroidPlatformTarget androidPlatformTarget;
  private final Path inputFile;
  private final Path outputFile;

  public ZipalignStep(
      BuildTarget buildTarget,
      Path workingDirectory,
      AndroidPlatformTarget androidPlatformTarget,
      Path inputFile,
      Path outputFile) {
    super(Optional.of(buildTarget), workingDirectory);
    this.androidPlatformTarget = androidPlatformTarget;
    this.inputFile = inputFile;
    this.outputFile = outputFile;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    ImmutableList.Builder<String> args = ImmutableList.builder();

    args.add(androidPlatformTarget.getZipalignExecutable().toString());
    args.add("-f").add("4");
    args.add(inputFile.toString());
    args.add(outputFile.toString());
    return args.build();
  }

  @Override
  public String getShortName() {
    return "zipalign";
  }
}
