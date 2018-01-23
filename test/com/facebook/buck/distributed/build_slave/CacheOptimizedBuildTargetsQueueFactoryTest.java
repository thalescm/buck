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

package com.facebook.buck.distributed.build_slave;

import static com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory.CACHABLE_C;
import static com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory.CHAIN_TOP_TARGET;
import static com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory.LEAF_TARGET;
import static com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory.LEFT_TARGET;
import static com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory.RIGHT_TARGET;
import static com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory.ROOT_TARGET;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import com.facebook.buck.distributed.ArtifactCacheByBuildRule;
import com.facebook.buck.distributed.testutil.CustomBuildRuleResolverFactory;
import com.facebook.buck.distributed.testutil.DummyArtifactCacheByBuildRule;
import com.facebook.buck.distributed.thrift.WorkUnit;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class CacheOptimizedBuildTargetsQueueFactoryTest {

  private ArtifactCacheByBuildRule artifactCache;
  private BuildRuleFinishedPublisher ruleFinishedPublisher;

  @Before
  public void setUp() {
    this.artifactCache = null;
    this.ruleFinishedPublisher = EasyMock.createMock(BuildRuleFinishedPublisher.class);
  }

  private BuildTargetsQueue createQueueWithLocalCacheHits(
      BuildRuleResolver resolver,
      Iterable<BuildTarget> topLevelTargets,
      List<BuildTarget> localCacheHitTargets) {
    artifactCache =
        new DummyArtifactCacheByBuildRule(
            ImmutableList.of(),
            localCacheHitTargets.stream().map(resolver::getRule).collect(Collectors.toList()));

    return new CacheOptimizedBuildTargetsQueueFactory(resolver, artifactCache, false)
        .createBuildTargetsQueue(topLevelTargets, ruleFinishedPublisher);
  }

  private BuildTargetsQueue createQueueWithRemoteCacheHits(
      BuildRuleResolver resolver,
      Iterable<BuildTarget> topLevelTargets,
      List<BuildTarget> remoteCacheHitTargets) {

    artifactCache =
        new DummyArtifactCacheByBuildRule(
            remoteCacheHitTargets.stream().map(resolver::getRule).collect(Collectors.toList()),
            ImmutableList.of());

    return new CacheOptimizedBuildTargetsQueueFactory(resolver, artifactCache, false)
        .createBuildTargetsQueue(topLevelTargets, ruleFinishedPublisher);
  }

  @Test
  public void testGraphWithCacheableAndUncachableRuntimeDepsForRemoteHitPruning()
      throws NoSuchBuildTargetException, InterruptedException {
    // Graph structure:
    //                        uncacheable_a (runtime)
    //                      /
    //       +- right (hit)-
    //       |              \
    // root -+               leaf (hit)
    //       |              /
    //       +- left (hit) -
    //                      \
    //                        {uncacheable_b (runtime), cacheable_c (runtime miss)}

    Capture<ImmutableList<String>> startedEventsCapture = Capture.newInstance();
    ruleFinishedPublisher.createBuildRuleStartedEvents(capture(startedEventsCapture));
    expectLastCall();

    Capture<ImmutableList<String>> completedEventsCapture = Capture.newInstance();
    ruleFinishedPublisher.createBuildRuleCompletionEvents(capture(completedEventsCapture));
    expectLastCall();

    replay(ruleFinishedPublisher);

    BuildRuleResolver resolver =
        CustomBuildRuleResolverFactory.createResolverWithUncacheableRuntimeDeps();
    BuildTarget rootTarget = BuildTargetFactory.newInstance(ROOT_TARGET);
    ImmutableList<BuildTarget> hitTargets =
        ImmutableList.of(
            BuildTargetFactory.newInstance(LEFT_TARGET),
            BuildTargetFactory.newInstance(RIGHT_TARGET),
            BuildTargetFactory.newInstance(LEAF_TARGET));
    BuildTargetsQueue queue =
        createQueueWithRemoteCacheHits(resolver, ImmutableList.of(rootTarget), hitTargets);

    List<WorkUnit> zeroDepTargets = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(1, zeroDepTargets.size());
    Assert.assertEquals(3, zeroDepTargets.get(0).getBuildTargets().size());

    // We should get a single chain of root <- left <- cacheable_c.
    Assert.assertEquals(CACHABLE_C, zeroDepTargets.get(0).getBuildTargets().get(0));
    Assert.assertEquals(LEFT_TARGET, zeroDepTargets.get(0).getBuildTargets().get(1));
    Assert.assertEquals(ROOT_TARGET, zeroDepTargets.get(0).getBuildTargets().get(2));

    List<WorkUnit> newZeroDepNodes =
        queue.dequeueZeroDependencyNodes(
            ImmutableList.of(CACHABLE_C, LEFT_TARGET, ROOT_TARGET),
            BuildTargetsQueueTest.MAX_UNITS_OF_WORK);
    Assert.assertEquals(0, newZeroDepNodes.size());

    // LEAF_TARGET and RIGHT_TARGET were pruned, so should have corresponding
    // started and completed events
    Assert.assertEquals(1, startedEventsCapture.getValues().size());
    Set<String> startedEvents = Sets.newHashSet(startedEventsCapture.getValues().get(0));
    Assert.assertTrue(startedEvents.contains(LEAF_TARGET));
    Assert.assertTrue(startedEvents.contains(RIGHT_TARGET));

    Assert.assertEquals(1, completedEventsCapture.getValues().size());
    Set<String> completedEvents = Sets.newHashSet(completedEventsCapture.getValues().get(0));
    Assert.assertTrue(completedEvents.contains(LEAF_TARGET));
    Assert.assertTrue(completedEvents.contains(RIGHT_TARGET));
  }

  @Test
  public void testTopLevelTargetWithCacheableRuntimeDepsIsNotSkipped()
      throws NoSuchBuildTargetException, InterruptedException {
    BuildRuleResolver resolver = CustomBuildRuleResolverFactory.createSimpleRuntimeDepsResolver();
    BuildTarget target =
        BuildTargetFactory.newInstance(CustomBuildRuleResolverFactory.HAS_RUNTIME_DEP_RULE);
    BuildTargetsQueue queue =
        createQueueWithRemoteCacheHits(
            resolver, ImmutableList.of(target), ImmutableList.of(target));
    List<WorkUnit> zeroDepTargets = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(1, zeroDepTargets.size());
    Assert.assertEquals(2, zeroDepTargets.get(0).getBuildTargets().size());

    // has_runtime_dep -> transitive_dep both form a chain, so should be returned as a work unit.
    Assert.assertEquals(
        CustomBuildRuleResolverFactory.TRANSITIVE_DEP_RULE,
        zeroDepTargets.get(0).getBuildTargets().get(0));
    Assert.assertEquals(
        CustomBuildRuleResolverFactory.HAS_RUNTIME_DEP_RULE,
        zeroDepTargets.get(0).getBuildTargets().get(1));
  }

  @Test
  public void testDiamondDependencyGraphWithRemoteCacheHits() throws NoSuchBuildTargetException {
    // Graph structure:
    //               / right (hit) \
    // root (miss) -                 - chain top (miss) - chain bottom (hit)
    //              \ left (miss) /

    BuildRuleResolver resolver =
        CustomBuildRuleResolverFactory.createDiamondDependencyResolverWithChainFromLeaf();
    BuildTarget rootTarget = BuildTargetFactory.newInstance(ROOT_TARGET);
    BuildTarget rightTarget = BuildTargetFactory.newInstance(RIGHT_TARGET);
    BuildTarget leafTarget = BuildTargetFactory.newInstance(LEAF_TARGET);

    BuildTargetsQueue queue =
        createQueueWithRemoteCacheHits(
            resolver, ImmutableList.of(rootTarget), ImmutableList.of(rightTarget, leafTarget));

    List<WorkUnit> zeroDepWorkUnits = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(1, zeroDepWorkUnits.size());
    WorkUnit workUnit = zeroDepWorkUnits.get(0);
    List<String> targets = workUnit.getBuildTargets();
    Assert.assertEquals(3, targets.size());
    Assert.assertEquals(CHAIN_TOP_TARGET, targets.get(0));
    Assert.assertEquals(LEFT_TARGET, targets.get(1));
    Assert.assertEquals(ROOT_TARGET, targets.get(2));

    zeroDepWorkUnits =
        queue.dequeueZeroDependencyNodes(targets, BuildTargetsQueueTest.MAX_UNITS_OF_WORK);
    Assert.assertEquals(0, zeroDepWorkUnits.size());
  }

  @Test
  public void testGraphWithTopLevelCacheHit() throws NoSuchBuildTargetException {
    BuildRuleResolver resolver = CustomBuildRuleResolverFactory.createSimpleResolver();
    BuildTarget target = BuildTargetFactory.newInstance(ROOT_TARGET);
    BuildTargetsQueue queue =
        createQueueWithRemoteCacheHits(
            resolver, ImmutableList.of(target), ImmutableList.of(target));
    List<WorkUnit> zeroDepTargets = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(0, zeroDepTargets.size());
  }

  @Test
  public void testUploadCriticalNodesFromLocalCache() {
    // Graph structure:
    //               / right (hit) \
    // root (miss) -                 - chain top (miss) - chain bottom (hit)
    //              \ left (miss) /

    BuildRuleResolver resolver =
        CustomBuildRuleResolverFactory.createDiamondDependencyResolverWithChainFromLeaf();
    BuildTarget rootTarget = BuildTargetFactory.newInstance(ROOT_TARGET);
    BuildTarget rightTarget = BuildTargetFactory.newInstance(RIGHT_TARGET);
    BuildTarget leafTarget = BuildTargetFactory.newInstance(LEAF_TARGET);

    ImmutableList<BuildTarget> localHitTargets = ImmutableList.of(rightTarget, leafTarget);
    BuildTargetsQueue queue =
        createQueueWithLocalCacheHits(resolver, ImmutableList.of(rootTarget), localHitTargets);

    List<WorkUnit> zeroDepWorkUnits = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(1, zeroDepWorkUnits.size());
    WorkUnit workUnit = zeroDepWorkUnits.get(0);
    List<String> targets = workUnit.getBuildTargets();
    Assert.assertEquals(3, targets.size());
    Assert.assertEquals(CHAIN_TOP_TARGET, targets.get(0));
    Assert.assertEquals(LEFT_TARGET, targets.get(1));
    Assert.assertEquals(ROOT_TARGET, targets.get(2));

    zeroDepWorkUnits =
        queue.dequeueZeroDependencyNodes(targets, BuildTargetsQueueTest.MAX_UNITS_OF_WORK);
    Assert.assertEquals(0, zeroDepWorkUnits.size());

    Assert.assertEquals(
        ImmutableSet.copyOf(localHitTargets),
        artifactCache
            .getAllUploadRuleFutures()
            .stream()
            .map(Futures::getUnchecked)
            .map(BuildRule::getBuildTarget)
            .collect(ImmutableSet.toImmutableSet()));
  }

  @Test
  @Ignore // TODO(shivanker): make this test pass.
  public void testGraphWithMissingRuntimeDepsForLocalHitUploads()
      throws NoSuchBuildTargetException, InterruptedException {
    // Graph structure:
    //                        uncacheable_a (runtime)
    //                      /
    //       +- right (hit)-
    //       |              \
    // root -+               leaf (hit)
    //       |              /
    //       +- left (hit) -
    //                      \
    //                        {uncacheable_b (runtime), cacheable_c (runtime)}
    BuildRuleResolver resolver =
        CustomBuildRuleResolverFactory.createResolverWithUncacheableRuntimeDeps();
    BuildTarget rootTarget = BuildTargetFactory.newInstance(ROOT_TARGET);
    BuildTarget leftTarget = BuildTargetFactory.newInstance(LEFT_TARGET);
    BuildTarget rightTarget = BuildTargetFactory.newInstance(RIGHT_TARGET);
    BuildTarget leafTarget = BuildTargetFactory.newInstance(LEAF_TARGET);

    ImmutableList<BuildTarget> hitTargets = ImmutableList.of(leftTarget, rightTarget, leafTarget);
    BuildTargetsQueue queue =
        createQueueWithLocalCacheHits(resolver, ImmutableList.of(rootTarget), hitTargets);

    List<WorkUnit> zeroDepTargets = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(1, zeroDepTargets.size());
    Assert.assertEquals(3, zeroDepTargets.get(0).getBuildTargets().size());

    // We should get a single chain of root <- left <- cacheable_c.
    Assert.assertEquals(CACHABLE_C, zeroDepTargets.get(0).getBuildTargets().get(0));
    Assert.assertEquals(LEFT_TARGET, zeroDepTargets.get(0).getBuildTargets().get(1));
    Assert.assertEquals(ROOT_TARGET, zeroDepTargets.get(0).getBuildTargets().get(2));

    List<WorkUnit> newZeroDepNodes =
        queue.dequeueZeroDependencyNodes(
            ImmutableList.of(CACHABLE_C, LEFT_TARGET, ROOT_TARGET),
            BuildTargetsQueueTest.MAX_UNITS_OF_WORK);
    Assert.assertEquals(0, newZeroDepNodes.size());

    Assert.assertEquals(
        ImmutableSet.of(leftTarget, rightTarget),
        artifactCache
            .getAllUploadRuleFutures()
            .stream()
            .map(Futures::getUnchecked)
            .map(BuildRule::getBuildTarget)
            .collect(ImmutableSet.toImmutableSet()));
  }

  @Test
  public void testGraphWithLocallyCachedRuntimeDepsForLocalHitUploads()
      throws NoSuchBuildTargetException, InterruptedException {
    // Graph structure:
    //                        uncacheable_a (runtime)
    //                      /
    //       +- right (hit)-
    //       |              \
    // root -+               leaf (hit)
    //       |              /
    //       +- left (hit) -
    //                      \
    //                        {uncacheable_b (runtime), cacheable_c (runtime, hit)}
    BuildRuleResolver resolver =
        CustomBuildRuleResolverFactory.createResolverWithUncacheableRuntimeDeps();
    BuildTarget rootTarget = BuildTargetFactory.newInstance(ROOT_TARGET);
    BuildTarget leftTarget = BuildTargetFactory.newInstance(LEFT_TARGET);
    BuildTarget rightTarget = BuildTargetFactory.newInstance(RIGHT_TARGET);
    BuildTarget leafTarget = BuildTargetFactory.newInstance(LEAF_TARGET);
    BuildTarget cachableTargetC = BuildTargetFactory.newInstance(CACHABLE_C);

    ImmutableList<BuildTarget> hitTargets =
        ImmutableList.of(leftTarget, rightTarget, leafTarget, cachableTargetC);
    BuildTargetsQueue queue =
        createQueueWithLocalCacheHits(resolver, ImmutableList.of(rootTarget), hitTargets);

    List<WorkUnit> zeroDepTargets = BuildTargetsQueueTest.dequeueNoFinishedTargets(queue);
    Assert.assertEquals(1, zeroDepTargets.size());
    Assert.assertEquals(1, zeroDepTargets.get(0).getBuildTargets().size());

    // We should just build the root remotely.
    Assert.assertEquals(ROOT_TARGET, zeroDepTargets.get(0).getBuildTargets().get(0));

    List<WorkUnit> newZeroDepNodes =
        queue.dequeueZeroDependencyNodes(
            ImmutableList.of(ROOT_TARGET), BuildTargetsQueueTest.MAX_UNITS_OF_WORK);
    Assert.assertEquals(0, newZeroDepNodes.size());

    // But we should still be uploading cachable c, because without that, fetching left would
    // trigger a build of c.
    Assert.assertEquals(
        ImmutableSet.of(leftTarget, rightTarget, cachableTargetC),
        artifactCache
            .getAllUploadRuleFutures()
            .stream()
            .map(Futures::getUnchecked)
            .map(BuildRule::getBuildTarget)
            .collect(ImmutableSet.toImmutableSet()));
  }
}
