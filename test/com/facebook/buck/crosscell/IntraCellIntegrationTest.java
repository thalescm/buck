/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.crosscell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.listener.BroadcastEventListener;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.facebook.buck.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.DefaultKnownBuildRuleTypesFactory;
import com.facebook.buck.rules.KnownBuildRuleTypesProvider;
import com.facebook.buck.rules.coercer.ConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.sandbox.TestSandboxExecutionStrategyFactory;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class IntraCellIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  @Ignore
  public void shouldTreatACellBoundaryAsAHardBuckPackageBoundary() {}

  @SuppressWarnings("PMD.EmptyCatchBlock")
  @Test
  public void shouldTreatCellBoundariesAsVisibilityBoundariesToo()
      throws IOException, InterruptedException, BuildFileParseException, BuildTargetException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "intracell/visibility", tmp);
    workspace.setUp();

    // We don't need to do a build. It's enough to just parse these things.
    Cell cell = workspace.asCell();
    KnownBuildRuleTypesProvider knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                new DefaultProcessExecutor(new TestConsole()),
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));

    TypeCoercerFactory coercerFactory = new DefaultTypeCoercerFactory();
    Parser parser =
        new Parser(
            new BroadcastEventListener(),
            cell.getBuckConfig().getView(ParserConfig.class),
            coercerFactory,
            new ConstructorArgMarshaller(coercerFactory),
            knownBuildRuleTypesProvider,
            new ExecutableFinder());

    // This parses cleanly
    parser.buildTargetGraph(
        BuckEventBusForTests.newInstance(),
        cell,
        false,
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
        ImmutableSet.of(
            BuildTargetFactory.newInstance(
                cell.getFilesystem().getRootPath(), "//just-a-directory:rule")));

    Cell childCell =
        cell.getCell(
            BuildTargetFactory.newInstance(
                workspace.getDestPath().resolve("child-repo"), "//:child-target"));

    try {
      // Whereas, because visibility is limited to the same cell, this won't.
      parser.buildTargetGraph(
          BuckEventBusForTests.newInstance(),
          childCell,
          false,
          MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
          ImmutableSet.of(
              BuildTargetFactory.newInstance(
                  childCell.getFilesystem().getRootPath(), "child//:child-target")));
      fail("Didn't expect parsing to work because of visibility");
    } catch (HumanReadableException e) {
      // This is expected
    }
  }

  @Test
  @Ignore
  public void allOutputsShouldBePlacedInTheSameRootOutputDirectory() {}

  @Test
  public void testEmbeddedBuckOut() throws IOException, InterruptedException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "intracell/visibility", tmp);
    workspace.setUp();
    Cell cell = workspace.asCell();
    assertEquals(cell.getFilesystem().getBuckPaths().getGenDir().toString(), "buck-out/gen");
    Cell childCell =
        cell.getCell(
            BuildTargetFactory.newInstance(
                workspace.getDestPath().resolve("child-repo"), "//:child-target"));
    assertEquals(
        childCell.getFilesystem().getBuckPaths().getGenDir().toString(),
        "../buck-out/cells/child/gen");
  }
}
