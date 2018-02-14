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

package com.facebook.buck.rules.macros;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.macros.MacroException;
import com.facebook.buck.query.QueryBuildTarget;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.query.Query;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Used to expand the macro {@literal $(query_targets "some(query(:expression))")} to the set of
 * targets matching the query. Example queries
 *
 * <pre>
 *   '$(query_targets "deps(:foo)")'
 *   '$(query_targets "filter(bar, classpath(:bar))")'
 *   '$(query_targets "attrfilter(annotation_processors, com.foo.Processor, deps(:app))")'
 * </pre>
 */
public class QueryTargetsMacroExpander extends QueryMacroExpander<QueryTargetsMacro> {
  public QueryTargetsMacroExpander(Optional<TargetGraph> targetGraph) {
    super(targetGraph);
  }

  @Override
  public Class<QueryTargetsMacro> getInputClass() {
    return QueryTargetsMacro.class;
  }

  @Override
  QueryTargetsMacro fromQuery(Query query) {
    return QueryTargetsMacro.of(query);
  }

  @Override
  public Arg expandFrom(
      BuildTarget target,
      CellPathResolver cellNames,
      BuildRuleResolver resolver,
      QueryTargetsMacro input,
      QueryResults precomputedQueryResults)
      throws MacroException {
    return new QueriedTargetsArg(
        precomputedQueryResults
            .results
            .stream()
            .map(
                queryTarget -> {
                  Preconditions.checkState(queryTarget instanceof QueryBuildTarget);
                  BuildRule rule =
                      resolver.getRule(((QueryBuildTarget) queryTarget).getBuildTarget());
                  return rule.getBuildTarget();
                })
            .sorted()
            .collect(ImmutableList.toImmutableList()));
  }

  @Override
  boolean detectsTargetGraphOnlyDeps() {
    return true;
  }

  private class QueriedTargetsArg implements Arg {
    @AddToRuleKey private final ImmutableList<BuildTarget> queriedTargets;

    public QueriedTargetsArg(ImmutableList<BuildTarget> queriedTargets) {
      this.queriedTargets = queriedTargets;
    }

    @Override
    public void appendToCommandLine(Consumer<String> consumer, SourcePathResolver pathResolver) {
      consumer.accept(
          queriedTargets.stream().map(Object::toString).collect(Collectors.joining(" ")));
    }
  }
}
