/*
 * Copyright 2018-present Facebook, Inc.
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

package com.facebook.buck.android.toolchain.impl;

import com.facebook.buck.android.AndroidBuckConfig;
import com.facebook.buck.android.toolchain.AndroidBuildToolsLocation;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.toolchain.ToolchainCreationContext;
import com.facebook.buck.toolchain.ToolchainFactory;
import com.facebook.buck.toolchain.ToolchainInstantiationException;
import com.facebook.buck.toolchain.ToolchainProvider;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.environment.Platform;
import java.util.Optional;

public class AndroidBuildToolsLocationFactory
    implements ToolchainFactory<AndroidBuildToolsLocation> {

  @Override
  public Optional<AndroidBuildToolsLocation> createToolchain(
      ToolchainProvider toolchainProvider, ToolchainCreationContext context) {
    AndroidBuckConfig androidBuckConfig =
        new AndroidBuckConfig(context.getBuckConfig(), Platform.detect());

    AndroidBuildToolsResolver androidBuildToolsResolver =
        new AndroidBuildToolsResolver(
            androidBuckConfig,
            toolchainProvider.getByName(AndroidSdkLocation.DEFAULT_NAME, AndroidSdkLocation.class));

    try {
      return Optional.of(
          AndroidBuildToolsLocation.of(androidBuildToolsResolver.getBuildToolsPath()));
    } catch (HumanReadableException e) {
      throw ToolchainInstantiationException.wrap(e);
    }
  }
}
