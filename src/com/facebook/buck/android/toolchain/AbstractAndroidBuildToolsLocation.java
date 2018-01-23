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

package com.facebook.buck.android.toolchain;

import com.facebook.buck.toolchain.Toolchain;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import java.nio.file.Path;
import org.immutables.value.Value;

@Value.Immutable(copy = false)
@BuckStyleTuple
public interface AbstractAndroidBuildToolsLocation extends Toolchain {

  String DEFAULT_NAME = "android-build-tools";

  /** @return {@code Path} pointing to Android SDK build tools */
  Path getBuildToolsPath();
}
