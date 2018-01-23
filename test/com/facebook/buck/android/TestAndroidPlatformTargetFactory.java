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

package com.facebook.buck.android;

import com.facebook.buck.android.toolchain.AndroidBuildToolsLocation;
import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.android.toolchain.impl.AndroidPlatformTargetProducer;
import com.facebook.buck.io.file.MorePathsForTests;
import java.util.Optional;

public class TestAndroidPlatformTargetFactory {

  public static AndroidPlatformTarget create() {
    AndroidPlatformTarget androidPlatformTarget =
        AndroidPlatformTargetProducer.getDefaultPlatformTarget(
            AndroidBuildToolsLocation.of(
                MorePathsForTests.rootRelativePath("AndroidSDK").resolve("build-tools")),
            AndroidSdkLocation.of(MorePathsForTests.rootRelativePath("AndroidSDK")),
            Optional.empty(),
            Optional.empty());
    return androidPlatformTarget;
  }
}
