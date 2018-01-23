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

package com.facebook.buck.android;

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.jvm.java.ExtraClasspathProvider;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.toolchain.ToolchainProvider;
import java.nio.file.Path;

public class AndroidClasspathProvider implements ExtraClasspathProvider {

  private final ToolchainProvider toolchainProvider;

  @AddToRuleKey private final String classpath = "android";

  public AndroidClasspathProvider(ToolchainProvider toolchainProvider) {
    this.toolchainProvider = toolchainProvider;
  }

  @Override
  public Iterable<Path> getExtraClasspath() {
    AndroidPlatformTarget androidPlatformTarget =
        toolchainProvider.getByName(
            AndroidPlatformTarget.DEFAULT_NAME, AndroidPlatformTarget.class);
    return androidPlatformTarget.getBootclasspathEntries();
  }
}
