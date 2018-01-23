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

package com.facebook.buck.rules;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.config.ActionGraphParallelizationMode;
import com.facebook.buck.cxx.CxxHeadersExperiment;
import com.facebook.buck.event.ActionGraphEvent;
import com.facebook.buck.event.BuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.ExperimentEvent;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.Pair;
import com.facebook.buck.rules.keys.ContentAgnosticRuleKeyFactory;
import com.facebook.buck.rules.keys.RuleKeyFieldLoader;
import com.facebook.buck.rules.keys.config.TestRuleKeyConfigurationFactory;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.timing.IncrementingFakeClock;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ActionGraphCacheTest {

  private static final boolean CHECK_GRAPHS = true;
  private static final boolean NOT_CHECK_GRAPHS = false;

  private TargetNode<?, ?> nodeA;
  private TargetNode<?, ?> nodeB;
  private TargetGraph targetGraph1;
  private TargetGraph targetGraph2;

  private BuckEventBus eventBus;
  private BlockingQueue<BuckEvent> trackedEvents = new LinkedBlockingQueue<>();
  private final int keySeed = 0;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Rule public TemporaryPaths tmpFilePath = new TemporaryPaths();

  @Before
  public void setUp() {
    // Creates the following target graph:
    //      A
    //     /
    //    B

    nodeB = createTargetNode("B");
    nodeA = createTargetNode("A", nodeB);
    targetGraph1 = TargetGraphFactory.newInstance(nodeA, nodeB);
    targetGraph2 = TargetGraphFactory.newInstance(nodeB);

    eventBus =
        BuckEventBusForTests.newInstance(new IncrementingFakeClock(TimeUnit.SECONDS.toNanos(1)));

    trackedEvents.clear();
    eventBus.register(
        new Object() {
          @Subscribe
          public void actionGraphCacheEvent(ActionGraphEvent.Cache event) {
            trackedEvents.add(event);
          }

          @Subscribe
          public void actionGraphCacheEvent(ExperimentEvent event) {
            trackedEvents.add(event);
          }
        });
  }

  @Test
  public void hitOnCache() throws InterruptedException {
    ActionGraphCache cache = new ActionGraphCache(1);

    ActionGraphAndResolver resultRun1 =
        cache.getActionGraph(
            eventBus,
            CHECK_GRAPHS, /* skipActionGraphCache */
            false,
            targetGraph1,
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.DISABLED,
            false);
    // The 1st time you query the ActionGraph it's a cache miss.
    assertEquals(countEventsOf(ActionGraphEvent.Cache.Hit.class), 0);
    assertEquals(countEventsOf(ActionGraphEvent.Cache.Miss.class), 1);

    ActionGraphAndResolver resultRun2 =
        cache.getActionGraph(
            eventBus,
            CHECK_GRAPHS, /* skipActionGraphCache */
            false,
            targetGraph1,
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.DISABLED,
            false);
    // The 2nd time it should be a cache hit and the ActionGraphs should be exactly the same.
    assertEquals(countEventsOf(ActionGraphEvent.Cache.Hit.class), 1);
    assertEquals(countEventsOf(ActionGraphEvent.Cache.Miss.class), 1);

    // Check all the RuleKeys are the same between the 2 ActionGraphs.
    Map<BuildRule, RuleKey> resultRun1RuleKeys =
        getRuleKeysFromBuildRules(resultRun1.getActionGraph().getNodes(), resultRun1.getResolver());
    Map<BuildRule, RuleKey> resultRun2RuleKeys =
        getRuleKeysFromBuildRules(resultRun2.getActionGraph().getNodes(), resultRun2.getResolver());

    assertThat(resultRun1RuleKeys, equalTo(resultRun2RuleKeys));
  }

  @Test
  public void hitOnMultiEntryCache() {
    ActionGraphCache cache = new ActionGraphCache(2);

    // List of (graph to run, (expected hit count, expected miss count))
    ArrayList<Pair<TargetGraph, Pair<Integer, Integer>>> runList = new ArrayList<>();

    // First run for graph 1 should be a miss.
    runList.add(new Pair<>(targetGraph1, new Pair<>(0, 1)));
    // First run for graph 2 should be a miss.
    runList.add(new Pair<>(targetGraph2, new Pair<>(0, 2)));
    // Second run for graph 2 should be a hit.
    runList.add(new Pair<>(targetGraph2, new Pair<>(1, 2)));
    // Second run for graph 1 should be a hit.
    runList.add(new Pair<>(targetGraph1, new Pair<>(2, 2)));
    // Third run for graph 2 should be a hit again.
    runList.add(new Pair<>(targetGraph2, new Pair<>(3, 2)));

    runAndCheckExpectedHitMissCount(cache, runList);
  }

  @Test
  public void testLruEvictionOrder() {
    ActionGraphCache cache = new ActionGraphCache(2);

    // List of (graph to run, (expected hit count, expected miss count))
    ArrayList<Pair<TargetGraph, Pair<Integer, Integer>>> runList = new ArrayList<>();

    // First run for graph 1 should be a miss.
    runList.add(new Pair<>(targetGraph1, new Pair<>(0, 1)));
    // First run for graph 2 should be a miss.
    runList.add(new Pair<>(targetGraph2, new Pair<>(0, 2)));
    // Run graph 1 again to make it the MRU.
    runList.add(new Pair<>(targetGraph1, new Pair<>(1, 2)));
    // Run empty graph to evict graph 2.
    runList.add(new Pair<>(TargetGraph.EMPTY, new Pair<>(1, 3)));
    // Another run with graph 2 should be a miss (it should have just been evicted)
    runList.add(new Pair<>(targetGraph2, new Pair<>(1, 4)));
    // Now cache order should be (by LRU): EMPTY, targetGraph2
    runList.add(new Pair<>(targetGraph1, new Pair<>(1, 5)));

    runAndCheckExpectedHitMissCount(cache, runList);
  }

  private void runAndCheckExpectedHitMissCount(
      ActionGraphCache cache, List<Pair<TargetGraph, Pair<Integer, Integer>>> runList) {
    for (Pair<TargetGraph, Pair<Integer, Integer>> run : runList) {
      cache.getActionGraph(
          eventBus,
          CHECK_GRAPHS, /* skipActionGraphCache */
          false,
          run.getFirst(),
          TestRuleKeyConfigurationFactory.create(),
          ActionGraphParallelizationMode.DISABLED,
          false);

      assertEquals(
          countEventsOf(ActionGraphEvent.Cache.Hit.class), (int) run.getSecond().getFirst());
      assertEquals(
          countEventsOf(ActionGraphEvent.Cache.Miss.class), (int) run.getSecond().getSecond());
    }
  }

  @Test
  public void missOnCache() {
    ActionGraphCache cache = new ActionGraphCache(1);
    ActionGraphAndResolver resultRun1 =
        cache.getActionGraph(
            eventBus,
            CHECK_GRAPHS, /* skipActionGraphCache */
            false,
            targetGraph1,
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.DISABLED,
            false);
    // Each time you call it for a different TargetGraph so all calls should be misses.
    assertEquals(0, countEventsOf(ActionGraphEvent.Cache.Hit.class));
    assertEquals(1, countEventsOf(ActionGraphEvent.Cache.Miss.class));

    trackedEvents.clear();
    ActionGraphAndResolver resultRun2 =
        cache.getActionGraph(
            eventBus,
            CHECK_GRAPHS,
            /* skipActionGraphCache */ false,
            targetGraph1.getSubgraph(ImmutableSet.of(nodeB)),
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.DISABLED,
            false);
    assertEquals(0, countEventsOf(ActionGraphEvent.Cache.Hit.class));
    assertEquals(1, countEventsOf(ActionGraphEvent.Cache.Miss.class));
    assertEquals(1, countEventsOf(ActionGraphEvent.Cache.MissWithTargetGraphDifference.class));

    trackedEvents.clear();
    ActionGraphAndResolver resultRun3 =
        cache.getActionGraph(
            eventBus,
            CHECK_GRAPHS, /* skipActionGraphCache */
            false,
            targetGraph1,
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.DISABLED,
            false);
    assertEquals(0, countEventsOf(ActionGraphEvent.Cache.Hit.class));
    assertEquals(1, countEventsOf(ActionGraphEvent.Cache.Miss.class));

    // Run1 and Run2 should not match, but Run1 and Run3 should
    Map<BuildRule, RuleKey> resultRun1RuleKeys =
        getRuleKeysFromBuildRules(resultRun1.getActionGraph().getNodes(), resultRun1.getResolver());
    Map<BuildRule, RuleKey> resultRun2RuleKeys =
        getRuleKeysFromBuildRules(resultRun2.getActionGraph().getNodes(), resultRun2.getResolver());
    Map<BuildRule, RuleKey> resultRun3RuleKeys =
        getRuleKeysFromBuildRules(resultRun3.getActionGraph().getNodes(), resultRun3.getResolver());

    // Run2 is done in a subgraph and it should not have the same ActionGraph.
    assertThat(resultRun1RuleKeys, Matchers.not(equalTo(resultRun2RuleKeys)));
    // Run1 and Run3 should match.
    assertThat(resultRun1RuleKeys, equalTo(resultRun3RuleKeys));
  }

  // If this breaks it probably means the ActionGraphCache checking also breaks.
  @Test
  public void compareActionGraphsBasedOnRuleKeys() {
    ActionGraphAndResolver resultRun1 =
        ActionGraphCache.getFreshActionGraph(
            eventBus,
            new DefaultTargetNodeToBuildRuleTransformer(),
            targetGraph1,
            ActionGraphParallelizationMode.DISABLED,
            false);

    ActionGraphAndResolver resultRun2 =
        ActionGraphCache.getFreshActionGraph(
            eventBus,
            new DefaultTargetNodeToBuildRuleTransformer(),
            targetGraph1,
            ActionGraphParallelizationMode.DISABLED,
            false);

    // Check all the RuleKeys are the same between the 2 ActionGraphs.
    Map<BuildRule, RuleKey> resultRun1RuleKeys =
        getRuleKeysFromBuildRules(resultRun1.getActionGraph().getNodes(), resultRun1.getResolver());
    Map<BuildRule, RuleKey> resultRun2RuleKeys =
        getRuleKeysFromBuildRules(resultRun2.getActionGraph().getNodes(), resultRun2.getResolver());

    assertThat(resultRun1RuleKeys, equalTo(resultRun2RuleKeys));
  }

  @Test
  public void actionGraphParallelizationStateIsLogged() throws Exception {
    List<ExperimentEvent> experimentEvents;

    for (ActionGraphParallelizationMode mode :
        ImmutableSet.of(
            ActionGraphParallelizationMode.DISABLED, ActionGraphParallelizationMode.ENABLED)) {
      new ActionGraphCache(1)
          .getActionGraph(
              eventBus,
              NOT_CHECK_GRAPHS, /* skipActionGraphCache */
              false,
              targetGraph1,
              TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
              mode,
              false);
      experimentEvents =
          RichStream.from(trackedEvents.stream())
              .filter(ExperimentEvent.class)
              .filter(
                  event ->
                      !(event.getEventName().equals(CxxSymlinkTreeHeadersExperiment.EXPERIMENT_NAME)
                          || event.getEventName().equals(CxxHeadersExperiment.EXPERIMENT_NAME)))
              .collect(Collectors.toList());
      assertThat(
          "No experiment event is logged if not in experiment mode", experimentEvents, empty());
    }

    trackedEvents.clear();
    new ActionGraphCache(1)
        .getActionGraph(
            eventBus,
            NOT_CHECK_GRAPHS, /* skipActionGraphCache */
            false,
            targetGraph1,
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.EXPERIMENT,
            false);
    experimentEvents =
        RichStream.from(trackedEvents.stream())
            .filter(ExperimentEvent.class)
            .filter(
                event ->
                    !(event.getEventName().equals(CxxSymlinkTreeHeadersExperiment.EXPERIMENT_NAME)
                        || event.getEventName().equals(CxxHeadersExperiment.EXPERIMENT_NAME)))
            .collect(Collectors.toList());
    assertThat(
        "EXPERIMENT mode should log either enabled or disabled.",
        experimentEvents,
        contains(
            allOf(
                hasProperty("tag", equalTo("action_graph_parallelization")),
                hasProperty("variant", anyOf(equalTo("ENABLED"), equalTo("DISABLED"))))));

    trackedEvents.clear();
    new ActionGraphCache(1)
        .getActionGraph(
            eventBus,
            NOT_CHECK_GRAPHS, /* skipActionGraphCache */
            false,
            targetGraph1,
            TestRuleKeyConfigurationFactory.createWithSeed(keySeed),
            ActionGraphParallelizationMode.EXPERIMENT_UNSTABLE,
            false);
    experimentEvents =
        RichStream.from(trackedEvents.stream())
            .filter(ExperimentEvent.class)
            .filter(
                event ->
                    !(event.getEventName().equals(CxxSymlinkTreeHeadersExperiment.EXPERIMENT_NAME)
                        || event.getEventName().equals(CxxHeadersExperiment.EXPERIMENT_NAME)))
            .collect(Collectors.toList());
    assertThat(
        "EXPERIMENT mode should log either enabled or disabled.",
        experimentEvents,
        contains(
            allOf(
                hasProperty("tag", equalTo("action_graph_parallelization_unstable")),
                hasProperty("variant", anyOf(equalTo("ENABLED"), equalTo("DISABLED"))))));
  }

  private TargetNode<?, ?> createTargetNode(String name, TargetNode<?, ?>... deps) {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:" + name);
    JavaLibraryBuilder targetNodeBuilder = JavaLibraryBuilder.createBuilder(buildTarget);
    for (TargetNode<?, ?> dep : deps) {
      targetNodeBuilder.addDep(dep.getBuildTarget());
    }
    return targetNodeBuilder.build();
  }

  private int countEventsOf(Class<? extends ActionGraphEvent> trackedClass) {
    int i = 0;
    for (BuckEvent event : trackedEvents) {
      if (trackedClass.isInstance(event)) {
        i++;
      }
    }
    return i;
  }

  private Map<BuildRule, RuleKey> getRuleKeysFromBuildRules(
      Iterable<BuildRule> buildRules, BuildRuleResolver buildRuleResolver) {
    RuleKeyFieldLoader ruleKeyFieldLoader =
        new RuleKeyFieldLoader(TestRuleKeyConfigurationFactory.create());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver pathResolver = DefaultSourcePathResolver.from(ruleFinder);
    ContentAgnosticRuleKeyFactory factory =
        new ContentAgnosticRuleKeyFactory(
            ruleKeyFieldLoader, pathResolver, ruleFinder, Optional.empty());

    HashMap<BuildRule, RuleKey> ruleKeysMap = new HashMap<>();

    for (BuildRule rule : buildRules) {
      ruleKeysMap.put(rule, factory.build(rule));
    }

    return ruleKeysMap;
  }
}
