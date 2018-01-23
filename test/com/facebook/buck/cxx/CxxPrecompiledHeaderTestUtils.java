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

package com.facebook.buck.cxx;

import static org.junit.Assume.assumeTrue;

import com.facebook.buck.util.environment.Platform;

public class CxxPrecompiledHeaderTestUtils {

  /**
   * Check that the current platform supports PCH. Uses {@link org.junit.Assume#assumeTrue}, so the
   * test is skipped if PCH not supported.
   */
  public static void assumePrecompiledHeadersAreSupported() {
    assumeTrue(Platform.detect() != Platform.WINDOWS);
  }
}
