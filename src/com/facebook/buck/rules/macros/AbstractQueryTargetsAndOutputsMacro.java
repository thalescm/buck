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

package com.facebook.buck.rules.macros;

import com.facebook.buck.rules.query.Query;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import org.immutables.value.Value;

/**
 * Class providing the type for macros used in `$(query_targets_and_outputs ...)` macro strings. The
 * implementation is provided by the {@link QueryMacro} base class.
 */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractQueryTargetsAndOutputsMacro extends QueryMacro {
  abstract String getSeparator();

  @Override
  public abstract Query getQuery();
}
