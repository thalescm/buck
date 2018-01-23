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

package com.facebook.buck.android.toolchain.ndk;

import com.facebook.buck.util.immutables.BuckStyleImmutable;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
interface AbstractNdkCxxPlatformCompiler {

  NdkCompilerType getType();

  /**
   * @return the compiler version, corresponding to either `gcc_version` or `clang_version` from the
   *     .buckconfig settings, depending on which compiler family was selected.
   */
  String getVersion();

  /**
   * @return the GCC compiler version. Since even runtimes which are not GCC-based need to use GCC
   *     tools (e.g. ar, as,, ld.gold), we need to *always* have a version of GCC.
   */
  String getGccVersion();
}
