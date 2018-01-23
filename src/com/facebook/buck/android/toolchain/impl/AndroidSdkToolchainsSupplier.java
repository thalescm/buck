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

package com.facebook.buck.android.toolchain.impl;

import com.facebook.buck.android.toolchain.AndroidBuildToolsLocation;
import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.android.toolchain.DxToolchain;
import com.facebook.buck.toolchain.ToolchainDescriptor;
import com.facebook.buck.toolchain.ToolchainSupplier;
import java.util.Arrays;
import java.util.Collection;
import org.pf4j.Extension;

@Extension
public class AndroidSdkToolchainsSupplier implements ToolchainSupplier {

  @Override
  public Collection<ToolchainDescriptor<?>> getToolchainDescriptor() {
    return Arrays.asList(
        ToolchainDescriptor.of(
            AndroidBuildToolsLocation.DEFAULT_NAME,
            AndroidBuildToolsLocation.class,
            AndroidBuildToolsLocationFactory.class),
        ToolchainDescriptor.of(
            AndroidPlatformTarget.DEFAULT_NAME,
            AndroidPlatformTarget.class,
            AndroidPlatformTargetFactory.class),
        ToolchainDescriptor.of(
            AndroidSdkLocation.DEFAULT_NAME,
            AndroidSdkLocation.class,
            AndroidSdkLocationFactory.class),
        ToolchainDescriptor.of(
            DxToolchain.DEFAULT_NAME, DxToolchain.class, DxToolchainFactory.class));
  }
}
