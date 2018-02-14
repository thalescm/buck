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

package com.facebook.buck.rules.macros;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetPattern;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.versions.TargetNodeTranslator;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.regex.Pattern;

/** Base class for <code>cxx_genrule</code> flags-based macros. */
public abstract class CxxGenruleFilterAndTargetsMacro implements Macro {

  public abstract Optional<Pattern> getFilter();

  public abstract ImmutableList<BuildTarget> getTargets();

  /**
   * @return a copy of this {@link CxxGenruleFilterAndTargetsMacro} with the given {@link
   *     BuildTarget}.
   */
  abstract CxxGenruleFilterAndTargetsMacro withTargets(Iterable<? extends BuildTarget> targets);

  @Override
  public Optional<Macro> translateTargets(
      CellPathResolver cellPathResolver,
      BuildTargetPatternParser<BuildTargetPattern> pattern,
      TargetNodeTranslator translator) {
    return translator.translate(cellPathResolver, pattern, getTargets()).map(this::withTargets);
  }
}
