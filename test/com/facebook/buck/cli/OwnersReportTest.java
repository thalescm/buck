/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.cli;

import static com.facebook.buck.rules.TestCellBuilder.createCellRoots;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.listener.BroadcastEventListener;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildFileTree;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.FilesystemBackedBuildFileTree;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.DefaultKnownBuildRuleTypesFactory;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.KnownBuildRuleTypesProvider;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.TargetNodeFactory;
import com.facebook.buck.rules.TestCellBuilder;
import com.facebook.buck.rules.coercer.ConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.sandbox.TestSandboxExecutionStrategyFactory;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.util.timing.FakeClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import org.immutables.value.Value;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;

/** Reports targets that own a specified list of files. */
public class OwnersReportTest {

  public static class FakeRuleDescription implements Description<FakeRuleDescriptionArg> {

    @Override
    public Class<FakeRuleDescriptionArg> getConstructorArgType() {
      return FakeRuleDescriptionArg.class;
    }

    @Override
    public BuildRule createBuildRule(
        TargetGraph targetGraph,
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        BuildRuleParams params,
        BuildRuleResolver resolver,
        CellPathResolver cellRoots,
        FakeRuleDescriptionArg args) {
      return new FakeBuildRule(buildTarget, projectFilesystem, params);
    }

    @BuckStyleImmutable
    @Value.Immutable
    interface AbstractFakeRuleDescriptionArg extends CommonDescriptionArg {
      ImmutableSet<Path> getInputs();
    }
  }

  private TargetNode<?, ?> createTargetNode(BuildTarget buildTarget, ImmutableSet<Path> inputs) {
    FakeRuleDescription description = new FakeRuleDescription();
    FakeRuleDescriptionArg arg =
        FakeRuleDescriptionArg.builder()
            .setName(buildTarget.getShortName())
            .setInputs(inputs)
            .build();
    try {
      return new TargetNodeFactory(new DefaultTypeCoercerFactory())
          .create(
              Hashing.sha1().hashString(buildTarget.getFullyQualifiedName(), UTF_8),
              description,
              arg,
              filesystem,
              buildTarget,
              ImmutableSet.of(),
              ImmutableSet.of(),
              ImmutableSet.of(),
              createCellRoots(filesystem));
    } catch (NoSuchBuildTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private ProjectFilesystem filesystem;

  @Before
  public void setUp() throws InterruptedException {
    filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
  }

  @Test
  public void verifyPathsThatAreNotFilesAreCorrectlyReported()
      throws CmdLineException, IOException, InterruptedException {
    filesystem.mkdirs(filesystem.getPath("java/somefolder/badfolder"));
    filesystem.mkdirs(filesystem.getPath("com/test/subtest"));

    // Inputs that should be treated as "non-files", i.e. as directories
    String input = "java/somefolder/badfolder";

    BuildTarget target = BuildTargetFactory.newInstance("//base:name");
    TargetNode<?, ?> targetNode = createTargetNode(target, ImmutableSet.of());

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report = OwnersReport.generateOwnersReport(cell, targetNode, input);
    assertTrue(report.owners.isEmpty());
    assertTrue(report.nonExistentInputs.isEmpty());
    assertTrue(report.inputsWithNoOwners.isEmpty());
    assertEquals(ImmutableSet.of(input), report.nonFileInputs);
  }

  @Test
  public void verifyMissingFilesAreCorrectlyReported()
      throws CmdLineException, IOException, InterruptedException {
    // Inputs that should be treated as missing files
    String input = "java/somefolder/badfolder/somefile.java";

    BuildTarget target = BuildTargetFactory.newInstance("//base:name");
    TargetNode<?, ?> targetNode = createTargetNode(target, ImmutableSet.of());

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report = OwnersReport.generateOwnersReport(cell, targetNode, input);
    assertTrue(report.owners.isEmpty());
    assertTrue(report.nonFileInputs.isEmpty());
    assertTrue(report.inputsWithNoOwners.isEmpty());
    assertEquals(ImmutableSet.of(input), report.nonExistentInputs);
  }

  @Test
  public void verifyInputsWithoutOwnersAreCorrectlyReported()
      throws CmdLineException, IOException, InterruptedException {
    // Inputs that should be treated as existing files
    String input = "java/somefolder/badfolder/somefile.java";
    Path inputPath = filesystem.getPath(input);

    // Write dummy files.
    filesystem.mkdirs(inputPath.getParent());
    filesystem.writeContentsToPath("", inputPath);

    BuildTarget target = BuildTargetFactory.newInstance("//base:name");
    TargetNode<?, ?> targetNode = createTargetNode(target, ImmutableSet.of());

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report = OwnersReport.generateOwnersReport(cell, targetNode, input);
    assertTrue(report.owners.isEmpty());
    assertTrue(report.nonFileInputs.isEmpty());
    assertTrue(report.nonExistentInputs.isEmpty());
    assertEquals(ImmutableSet.of(inputPath), report.inputsWithNoOwners);
  }

  @Test
  public void verifyInputsAgainstRulesThatListDirectoryInputs()
      throws IOException, InterruptedException {
    // Inputs that should be treated as existing files
    String input = "java/somefolder/badfolder/somefile.java";
    Path inputPath = filesystem.getPath(input);

    // Write dummy files.
    filesystem.mkdirs(inputPath.getParent());
    filesystem.writeContentsToPath("", inputPath);

    BuildTarget target = BuildTargetFactory.newInstance("//base:name");
    TargetNode<?, ?> targetNode =
        createTargetNode(target, ImmutableSet.of(filesystem.getPath("java/somefolder")));

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report = OwnersReport.generateOwnersReport(cell, targetNode, input);
    assertTrue(report.owners.containsKey(targetNode));
    assertEquals(ImmutableSet.of(inputPath), report.owners.get(targetNode));
    assertTrue(report.nonFileInputs.isEmpty());
    assertTrue(report.nonExistentInputs.isEmpty());
    assertTrue(report.inputsWithNoOwners.isEmpty());
  }

  /** Verify that owners are correctly detected: - one owner, multiple inputs */
  @Test
  public void verifyInputsWithOneOwnerAreCorrectlyReported()
      throws CmdLineException, IOException, InterruptedException {

    ImmutableList<String> inputs =
        ImmutableList.of("java/somefolder/badfolder/somefile.java", "java/somefolder/perfect.java");
    ImmutableSet<Path> inputPaths =
        RichStream.from(inputs).map(filesystem::getPath).toImmutableSet();

    for (Path path : inputPaths) {
      filesystem.mkdirs(path.getParent());
      filesystem.writeContentsToPath("", path);
    }

    BuildTarget target = BuildTargetFactory.newInstance("//base:name");
    TargetNode<?, ?> targetNode = createTargetNode(target, inputPaths);

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report1 = OwnersReport.generateOwnersReport(cell, targetNode, inputs.get(0));
    OwnersReport report2 = OwnersReport.generateOwnersReport(cell, targetNode, inputs.get(1));
    OwnersReport report = report1.updatedWith(report2);

    assertTrue(report.nonFileInputs.isEmpty());
    assertTrue(report.nonExistentInputs.isEmpty());
    assertTrue(report.inputsWithNoOwners.isEmpty());

    assertEquals(inputs.size(), report.owners.size());
    assertTrue(report.owners.containsKey(targetNode));
    assertEquals(targetNode.getInputs(), report.owners.get(targetNode));
  }

  /** Verify that owners are correctly detected: - inputs that belong to multiple targets */
  @Test
  public void verifyInputsWithMultipleOwnersAreCorrectlyReported()
      throws CmdLineException, IOException, InterruptedException {
    String input = "java/somefolder/badfolder/somefile.java";
    Path inputPath = filesystem.getPath(input);

    filesystem.mkdirs(inputPath.getParent());
    filesystem.writeContentsToPath("", inputPath);

    BuildTarget target1 = BuildTargetFactory.newInstance("//base/name1:name");
    BuildTarget target2 = BuildTargetFactory.newInstance("//base/name2:name");
    TargetNode<?, ?> targetNode1 = createTargetNode(target1, ImmutableSet.of(inputPath));
    TargetNode<?, ?> targetNode2 = createTargetNode(target2, ImmutableSet.of(inputPath));

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report = OwnersReport.generateOwnersReport(cell, targetNode1, input);
    report = report.updatedWith(OwnersReport.generateOwnersReport(cell, targetNode2, input));

    assertTrue(report.nonFileInputs.isEmpty());
    assertTrue(report.nonExistentInputs.isEmpty());
    assertTrue(report.inputsWithNoOwners.isEmpty());

    assertTrue(report.owners.containsKey(targetNode1));
    assertTrue(report.owners.containsKey(targetNode2));
    assertEquals(targetNode1.getInputs(), report.owners.get(targetNode1));
    assertEquals(targetNode2.getInputs(), report.owners.get(targetNode2));
  }

  @Test
  public void verifyThatRequestedFilesThatDoNotExistOnDiskAreReported()
      throws IOException, InterruptedException {
    String input = "java/some_file";

    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    OwnersReport report =
        OwnersReport.builder(
                cell, createParser(cell), BuckEventBusForTests.newInstance(FakeClock.doNotCare()))
            .build(
                getBuildFileTrees(cell),
                MoreExecutors.newDirectExecutorService(),
                ImmutableSet.of(input));

    assertEquals(1, report.nonExistentInputs.size());
    assertTrue(report.nonExistentInputs.contains(input));
  }

  private Parser createParser(Cell cell) {
    ProcessExecutor processExecutor = new DefaultProcessExecutor(new TestConsole());
    KnownBuildRuleTypesProvider knownBuildRuleTypesProvider =
        KnownBuildRuleTypesProvider.of(
            DefaultKnownBuildRuleTypesFactory.of(
                processExecutor,
                BuckPluginManagerFactory.createPluginManager(),
                new TestSandboxExecutionStrategyFactory()));
    TypeCoercerFactory coercerFactory = new DefaultTypeCoercerFactory();
    return new Parser(
        new BroadcastEventListener(),
        cell.getBuckConfig().getView(ParserConfig.class),
        coercerFactory,
        new ConstructorArgMarshaller(coercerFactory),
        knownBuildRuleTypesProvider);
  }

  private ImmutableMap<Cell, BuildFileTree> getBuildFileTrees(Cell rootCell) {
    return rootCell
        .getAllCells()
        .stream()
        .collect(
            ImmutableMap.toImmutableMap(
                Function.identity(),
                cell ->
                    new FilesystemBackedBuildFileTree(
                        cell.getFilesystem(), cell.getBuildFileName())));
  }
}
