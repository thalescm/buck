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

package com.facebook.buck.parser;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.PerfEventId;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.PipelineNodeCache.Cache;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.KnownBuildRuleTypes;
import com.facebook.buck.rules.KnownBuildRuleTypesProvider;
import com.facebook.buck.rules.TargetNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This implements a multithreaded pipeline for parsing BUCK files.
 *
 * <p>The high-level flow looks like this: (in) BuildTarget -> [getRawNodes] -*> [createTargetNode]
 * -> (out) TargetNode (steps in [] have their output cached, -*> means that this step has parallel
 * fanout).
 *
 * <p>The work is simply dumped onto the executor, the {@link ProjectBuildFileParserPool} is used to
 * constrain the number of concurrent active parsers. Within a single pipeline instance work is not
 * duplicated (the **JobsCache variables) are used to make sure we don't schedule the same work more
 * than once), however it's possible for multiple read-only commands to duplicate work.
 */
@ThreadSafe
public class TargetNodeParsePipeline
    extends ConvertingPipeline<Map<String, Object>, TargetNode<?, ?>> {

  private static final Logger LOG = Logger.get(TargetNodeParsePipeline.class);

  private final ParserTargetNodeFactory<TargetNode<?, ?>> delegate;
  private final BuckEventBus eventBus;
  private final boolean speculativeDepsTraversal;
  private final RawNodeParsePipeline rawNodeParsePipeline;
  private final SimplePerfEvent.Scope targetNodePipelineLifetimeEventScope;
  private final KnownBuildRuleTypesProvider knownBuildRuleTypesProvider;

  /**
   * Create new pipeline for parsing Buck files.
   *
   * @param cache where to persist results.
   * @param targetNodeDelegate where to farm out the creation of TargetNodes to
   * @param executorService executor
   * @param eventBus bus to use for parse start/stop events
   * @param speculativeDepsTraversal whether to automatically schedule parsing of nodes' deps in the
   * @param rawNodeParsePipeline
   */
  public TargetNodeParsePipeline(
      Cache<BuildTarget, TargetNode<?, ?>> cache,
      ParserTargetNodeFactory<TargetNode<?, ?>> targetNodeDelegate,
      ListeningExecutorService executorService,
      BuckEventBus eventBus,
      boolean speculativeDepsTraversal,
      RawNodeParsePipeline rawNodeParsePipeline,
      KnownBuildRuleTypesProvider knownBuildRuleTypesProvider) {
    super(executorService, cache);

    this.delegate = targetNodeDelegate;
    this.eventBus = eventBus;
    this.speculativeDepsTraversal = speculativeDepsTraversal;
    this.rawNodeParsePipeline = rawNodeParsePipeline;
    this.targetNodePipelineLifetimeEventScope =
        SimplePerfEvent.scope(eventBus, PerfEventId.of("target_node_parse_pipeline"));
    this.knownBuildRuleTypesProvider = knownBuildRuleTypesProvider;
  }

  @Override
  protected BuildTarget getBuildTarget(
      Path root, Optional<String> cellName, Path buildFile, Map<String, Object> from) {
    return BuildTarget.of(
        RawNodeParsePipeline.parseBuildTargetFromRawRule(root, cellName, from, buildFile));
  }

  @Override
  @SuppressWarnings("CheckReturnValue")
  protected TargetNode<?, ?> computeNode(
      final Cell cell,
      final KnownBuildRuleTypes knownBuildRuleTypes,
      final BuildTarget buildTarget,
      final Map<String, Object> rawNode,
      AtomicLong processedBytes)
      throws BuildTargetException {
    try (final SimplePerfEvent.Scope scope =
        SimplePerfEvent.scopeIgnoringShortEvents(
            eventBus,
            PerfEventId.of("GetTargetNode"),
            "target",
            buildTarget,
            targetNodePipelineLifetimeEventScope,
            getMinimumPerfEventTimeMs(),
            TimeUnit.MILLISECONDS)) {
      Function<PerfEventId, SimplePerfEvent.Scope> perfEventScopeFunction =
          perfEventId ->
              SimplePerfEvent.scopeIgnoringShortEvents(
                  eventBus, perfEventId, scope, getMinimumPerfEventTimeMs(), TimeUnit.MILLISECONDS);
      final TargetNode<?, ?> targetNode =
          delegate.createTargetNode(
              cell,
              knownBuildRuleTypes,
              cell.getAbsolutePathToBuildFile(buildTarget),
              buildTarget,
              rawNode,
              perfEventScopeFunction);

      if (speculativeDepsTraversal) {
        executorService.submit(
            () -> {
              for (BuildTarget depTarget : targetNode.getParseDeps()) {
                Cell depCell = cell.getCellIgnoringVisibilityCheck(depTarget.getCellPath());
                KnownBuildRuleTypes depKnownBuildRuleTypes =
                    knownBuildRuleTypesProvider.get(depCell);
                try {
                  if (depTarget.isFlavored()) {
                    getNodeJob(
                        depCell,
                        depKnownBuildRuleTypes,
                        BuildTarget.of(depTarget.getUnflavoredBuildTarget()),
                        processedBytes);
                  }
                  getNodeJob(depCell, depKnownBuildRuleTypes, depTarget, processedBytes);
                } catch (BuildTargetException e) {
                  // No biggie, we'll hit the error again in the non-speculative path.
                  LOG.info(e, "Could not schedule speculative parsing for %s", depTarget);
                }
              }
            });
      }

      return targetNode;
    }
  }

  @Override
  protected ListenableFuture<ImmutableSet<Map<String, Object>>> getItemsToConvert(
      Cell cell, KnownBuildRuleTypes knownBuildRuleTypes, Path buildFile, AtomicLong processedBytes)
      throws BuildTargetException {
    return rawNodeParsePipeline.getAllNodesJob(
        cell, knownBuildRuleTypes, buildFile, processedBytes);
  }

  @Override
  protected ListenableFuture<Map<String, Object>> getItemToConvert(
      Cell cell,
      KnownBuildRuleTypes knownBuildRuleTypes,
      BuildTarget buildTarget,
      AtomicLong processedBytes)
      throws BuildTargetException {
    return rawNodeParsePipeline.getNodeJob(cell, knownBuildRuleTypes, buildTarget, processedBytes);
  }

  @Override
  public void close() {
    targetNodePipelineLifetimeEventScope.close();
    super.close();
  }
}
