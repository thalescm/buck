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

package com.facebook.buck.rules;

import com.facebook.buck.model.BuildTarget;

/**
 * A {@link SourcePath} which resolves to some output generated by a {@link BuildRule}.
 *
 * <p>The file is not guaranteed to exist until the {@link BuildRule} has been built.
 */
public interface BuildTargetSourcePath extends SourcePath {

  /** @return the target of the {@link BuildRule} which generates the file. */
  BuildTarget getTarget();

  /**
   * @return a string representation that is usable for rule keys, i.e. does not depend on absolute
   *     paths
   */
  default String representationForRuleKey() {
    return toString();
  }
}
