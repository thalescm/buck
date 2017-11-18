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

package com.facebook.buck.rules;

import static com.facebook.buck.io.Watchman.NULL_WATCHMAN;

import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.config.FakeBuckConfig;
import com.facebook.buck.io.Watchman;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.DefaultProjectFilesystemFactory;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.toolchain.impl.TestToolchainProvider;
import java.io.IOException;
import javax.annotation.Nullable;

public class TestCellBuilder {

  private ProjectFilesystem filesystem;
  private BuckConfig buckConfig;
  private Watchman watchman = NULL_WATCHMAN;
  private CellConfig cellConfig;
  private SdkEnvironment sdkEnvironment;
  private ToolchainProvider toolchainProvider;

  public TestCellBuilder() throws InterruptedException, IOException {
    filesystem = new FakeProjectFilesystem();
    cellConfig = CellConfig.of();
  }

  public TestCellBuilder setFilesystem(ProjectFilesystem filesystem) {
    this.filesystem = filesystem;
    return this;
  }

  public TestCellBuilder setBuckConfig(BuckConfig buckConfig) {
    this.buckConfig = buckConfig;
    return this;
  }

  public TestCellBuilder setWatchman(Watchman watchman) {
    this.watchman = watchman;
    return this;
  }

  public TestCellBuilder setCellConfigOverride(CellConfig cellConfig) {
    this.cellConfig = cellConfig;
    return this;
  }

  public TestCellBuilder setSdkEnvironment(SdkEnvironment sdkEnvironment) {
    this.sdkEnvironment = sdkEnvironment;
    return this;
  }

  public TestCellBuilder setToolchainProvider(ToolchainProvider toolchainProvider) {
    this.toolchainProvider = toolchainProvider;
    return this;
  }

  public Cell build() throws IOException, InterruptedException {
    BuckConfig config =
        buckConfig == null
            ? FakeBuckConfig.builder().setFilesystem(filesystem).build()
            : buckConfig;

    ToolchainProvider toolchainProvider =
        this.toolchainProvider == null ? new TestToolchainProvider() : this.toolchainProvider;

    SdkEnvironment sdkEnvironment =
        this.sdkEnvironment == null
            ? SdkEnvironment.create(toolchainProvider)
            : this.sdkEnvironment;

    return CellProvider.createForLocalBuild(
            filesystem,
            watchman,
            config,
            cellConfig,
            sdkEnvironment,
            new DefaultProjectFilesystemFactory())
        .getCellByPath(filesystem.getRootPath());
  }

  public static CellPathResolver createCellRoots(@Nullable ProjectFilesystem filesystem) {
    ProjectFilesystem toUse = filesystem == null ? new FakeProjectFilesystem() : filesystem;
    return TestCellPathResolver.get(toUse);
  }
}
