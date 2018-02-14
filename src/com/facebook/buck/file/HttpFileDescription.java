/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.file;

import com.facebook.buck.file.downloader.Downloader;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.hash.HashCode;
import java.util.Optional;
import java.util.function.Supplier;
import org.immutables.value.Value;

/**
 * A description for downloading a single HttpFile (versus the combo logic contained in {@link
 * RemoteFileDescription}.
 */
public class HttpFileDescription implements Description<HttpFileDescriptionArg> {

  private final Supplier<Downloader> downloaderSupplier;

  public HttpFileDescription(ToolchainProvider toolchainProvider) {
    this.downloaderSupplier =
        () -> toolchainProvider.getByName(Downloader.DEFAULT_NAME, Downloader.class);
  }

  public HttpFileDescription(Downloader downloader) {
    this.downloaderSupplier = () -> downloader;
  }

  @Override
  public Class<HttpFileDescriptionArg> getConstructorArgType() {
    return HttpFileDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      HttpFileDescriptionArg args) {

    HashCode sha256 =
        HttpCommonDescriptionArg.HttpCommonDescriptionArgHelpers.parseSha256(
            args.getSha256(), buildTarget);
    HttpCommonDescriptionArg.HttpCommonDescriptionArgHelpers.validateUris(
        args.getUrls(), buildTarget);

    String out = args.getOut().orElse(buildTarget.getShortNameAndFlavorPostfix());

    boolean executable = args.getExecutable().orElse(false);
    if (executable) {
      return new HttpFileBinary(
          buildTarget,
          projectFilesystem,
          params,
          downloaderSupplier.get(),
          args.getUrls(),
          sha256,
          out);
    }
    return new HttpFile(
        buildTarget,
        projectFilesystem,
        params,
        downloaderSupplier.get(),
        args.getUrls(),
        sha256,
        out,
        false);
  }

  /** Args required for http_rule */
  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractHttpFileDescriptionArg extends HttpCommonDescriptionArg {
    Optional<Boolean> getExecutable();
  }
}
