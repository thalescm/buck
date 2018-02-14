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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.query.QueryBuildTarget;
import com.facebook.buck.query.QueryException;
import com.facebook.buck.query.QueryExpression;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.query.GraphEnhancementQueryEnvironment;
import com.facebook.buck.rules.query.Query;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/** Coercer for {@link Query}s. */
public class QueryCoercer implements TypeCoercer<Query> {

  private static final TypeCoercerFactory TYPE_COERCER_FACTORY = new DefaultTypeCoercerFactory();

  private Stream<BuildTarget> extractBuildTargets(CellPathResolver cellPathResolver, Query query) {
    GraphEnhancementQueryEnvironment env =
        new GraphEnhancementQueryEnvironment(
            Optional.empty(),
            Optional.empty(),
            TYPE_COERCER_FACTORY,
            cellPathResolver,
            query
                .getBaseName()
                .map(BuildTargetPatternParser::forBaseName)
                .orElseGet(BuildTargetPatternParser::fullyQualified),
            ImmutableSet.of());
    QueryExpression parsedExp;
    try {
      parsedExp = QueryExpression.parse(query.getQuery(), env);
    } catch (QueryException e) {
      throw new RuntimeException("Error parsing query: " + query.getQuery(), e);
    }
    return parsedExp
        .getTargets(env)
        .stream()
        .map(
            queryTarget -> {
              Preconditions.checkState(queryTarget instanceof QueryBuildTarget);
              return ((QueryBuildTarget) queryTarget).getBuildTarget();
            });
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    for (Class<?> type : types) {
      if (type.isAssignableFrom(BuildTarget.class)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void traverse(CellPathResolver cellRoots, Query query, Traversal traversal) {
    extractBuildTargets(cellRoots, query).forEach(traversal::traverse);
  }

  @Override
  public Class<Query> getOutputClass() {
    return Query.class;
  }

  @Override
  public Query coerce(
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      Path pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (object instanceof String) {
      return Query.of((String) object, "//" + pathRelativeToProjectRoot.toString());
    }
    throw CoerceFailedException.simple(object, getOutputClass());
  }
}
