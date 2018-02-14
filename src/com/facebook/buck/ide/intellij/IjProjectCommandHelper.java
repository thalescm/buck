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

package com.facebook.buck.ide.intellij;

import com.facebook.buck.cli.parameter_extractors.ProjectGeneratorParameters;
import com.facebook.buck.cli.parameter_extractors.ProjectViewParameters;
import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.config.ProjectTestsMode;
import com.facebook.buck.config.resources.ResourcesConfig;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.ide.intellij.model.IjProjectConfig;
import com.facebook.buck.ide.intellij.projectview.ProjectView;
import com.facebook.buck.jvm.core.JavaPackageFinder;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.java.JavaFileParser;
import com.facebook.buck.jvm.java.JavaLibraryDescription;
import com.facebook.buck.jvm.java.JavaLibraryDescriptionArg;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.BuildFileSpec;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.parser.PerBuildState;
import com.facebook.buck.parser.TargetNodePredicateSpec;
import com.facebook.buck.parser.TargetNodeSpec;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.facebook.buck.rules.ActionGraphAndResolver;
import com.facebook.buck.rules.ActionGraphCache;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetGraphAndTargets;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.rules.keys.config.RuleKeyConfiguration;
import com.facebook.buck.util.CloseableMemoizedSupplier;
import com.facebook.buck.util.CommandLineException;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.MoreExceptions;
import com.facebook.buck.util.concurrent.MostExecutors;
import com.facebook.buck.versions.InstrumentedVersionedTargetGraphCache;
import com.facebook.buck.versions.VersionException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

public class IjProjectCommandHelper {

  private final BuckEventBus buckEventBus;
  private final Console console;
  private final ListeningExecutorService executor;
  private final Parser parser;
  private final BuckConfig buckConfig;
  private final ActionGraphCache actionGraphCache;
  private final InstrumentedVersionedTargetGraphCache versionedTargetGraphCache;
  private final TypeCoercerFactory typeCoercerFactory;
  private final Cell cell;
  private final RuleKeyConfiguration ruleKeyConfiguration;
  private final IjProjectConfig projectConfig;
  private final boolean enableParserProfiling;
  private final BuckBuildRunner buckBuildRunner;
  private final Function<Iterable<String>, ImmutableList<TargetNodeSpec>> argsParser;

  private final ProjectGeneratorParameters projectGeneratorParameters;
  private final ProjectViewParameters projectViewParameters;

  public IjProjectCommandHelper(
      BuckEventBus buckEventBus,
      ListeningExecutorService executor,
      BuckConfig buckConfig,
      ActionGraphCache actionGraphCache,
      InstrumentedVersionedTargetGraphCache versionedTargetGraphCache,
      TypeCoercerFactory typeCoercerFactory,
      Cell cell,
      RuleKeyConfiguration ruleKeyConfiguration,
      IjProjectConfig projectConfig,
      boolean enableParserProfiling,
      BuckBuildRunner buckBuildRunner,
      Function<Iterable<String>, ImmutableList<TargetNodeSpec>> argsParser,
      ProjectViewParameters projectViewParameters) {
    this.buckEventBus = buckEventBus;
    this.console = projectViewParameters.getConsole();
    this.executor = executor;
    this.parser = projectViewParameters.getParser();
    this.buckConfig = buckConfig;
    this.actionGraphCache = actionGraphCache;
    this.versionedTargetGraphCache = versionedTargetGraphCache;
    this.typeCoercerFactory = typeCoercerFactory;
    this.cell = cell;
    this.ruleKeyConfiguration = ruleKeyConfiguration;
    this.projectConfig = projectConfig;
    this.enableParserProfiling = enableParserProfiling;
    this.buckBuildRunner = buckBuildRunner;
    this.argsParser = argsParser;

    this.projectGeneratorParameters = projectViewParameters;
    this.projectViewParameters = projectViewParameters;
  }

  public ExitCode parseTargetsAndRunProjectGenerator(List<String> arguments)
      throws IOException, InterruptedException {
    if (projectViewParameters.hasViewPath() && arguments.isEmpty()) {
      throw new CommandLineException(
          "params are view_path target(s), but you didn't supply any targets");
    }

    if (projectViewParameters.hasViewPath()) {
      console.printErrorText("`--view` option is deprecated and will be removed soon.");
    }

    List<String> targets = arguments;
    if (targets.isEmpty()) {
      targets = ImmutableList.of("//...");
    }

    ImmutableSet<BuildTarget> passedInTargetsSet;
    TargetGraph projectGraph;

    try {
      ParserConfig parserConfig = buckConfig.getView(ParserConfig.class);
      passedInTargetsSet =
          ImmutableSet.copyOf(
              Iterables.concat(
                  parser.resolveTargetSpecs(
                      buckEventBus,
                      cell,
                      enableParserProfiling,
                      executor,
                      argsParser.apply(targets),
                      PerBuildState.SpeculativeParsing.ENABLED,
                      parserConfig.getDefaultFlavorsMode())));
      projectGraph = getProjectGraphForIde(executor, passedInTargetsSet);
    } catch (BuildFileParseException e) {
      buckEventBus.post(ConsoleEvent.severe(MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return ExitCode.PARSE_ERROR;
    } catch (HumanReadableException e) {
      buckEventBus.post(ConsoleEvent.severe(MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return ExitCode.BUILD_ERROR;
    }

    ImmutableSet<BuildTarget> graphRoots;
    if (passedInTargetsSet.isEmpty()) {
      graphRoots =
          projectGraph
              .getNodes()
              .stream()
              .map(TargetNode::getBuildTarget)
              .collect(ImmutableSet.toImmutableSet());
    } else {
      graphRoots = passedInTargetsSet;
    }

    TargetGraphAndTargets targetGraphAndTargets;
    try {
      targetGraphAndTargets =
          createTargetGraph(projectGraph, graphRoots, passedInTargetsSet.isEmpty(), executor);
    } catch (BuildFileParseException | TargetGraph.NoSuchNodeException | VersionException e) {
      buckEventBus.post(ConsoleEvent.severe(MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return ExitCode.PARSE_ERROR;
    } catch (HumanReadableException e) {
      buckEventBus.post(ConsoleEvent.severe(MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return ExitCode.BUILD_ERROR;
    }

    if (projectViewParameters.hasViewPath()) {
      if (isWithTests()) {
        projectGraph = targetGraphAndTargets.getTargetGraph();
      }

      return ExitCode.map(
          ProjectView.run(
              projectViewParameters,
              projectGraph,
              passedInTargetsSet,
              getActionGraph(projectGraph)));
    }

    if (projectGeneratorParameters.isDryRun()) {
      for (TargetNode<?, ?> targetNode : targetGraphAndTargets.getTargetGraph().getNodes()) {
        console.getStdOut().println(targetNode.toString());
      }

      return ExitCode.SUCCESS;
    }

    return runIntellijProjectGenerator(targetGraphAndTargets);
  }

  private ActionGraphAndResolver getActionGraph(TargetGraph targetGraph) {
    try (CloseableMemoizedSupplier<ForkJoinPool, RuntimeException> forkJoinPoolSupplier =
        CloseableMemoizedSupplier.of(
            () ->
                MostExecutors.forkJoinPoolWithThreadLimit(
                    buckConfig.getView(ResourcesConfig.class).getDefaultResourceAmounts().getCpu(),
                    16),
            ForkJoinPool::shutdownNow)) {
      return actionGraphCache.getActionGraph(
          buckEventBus, targetGraph, buckConfig, ruleKeyConfiguration, forkJoinPoolSupplier);
    }
  }

  private TargetGraph getProjectGraphForIde(
      ListeningExecutorService executor, ImmutableSet<BuildTarget> passedInTargets)
      throws InterruptedException, BuildFileParseException, BuildTargetException, IOException {

    if (passedInTargets.isEmpty()) {
      return parser
          .buildTargetGraphForTargetNodeSpecs(
              buckEventBus,
              cell,
              enableParserProfiling,
              executor,
              ImmutableList.of(
                  TargetNodePredicateSpec.of(
                      BuildFileSpec.fromRecursivePath(Paths.get(""), cell.getRoot()))))
          .getTargetGraph();
    }
    Preconditions.checkState(!passedInTargets.isEmpty());
    return parser.buildTargetGraph(
        buckEventBus, cell, enableParserProfiling, executor, passedInTargets);
  }

  /** Run intellij specific project generation actions. */
  private ExitCode runIntellijProjectGenerator(final TargetGraphAndTargets targetGraphAndTargets)
      throws IOException, InterruptedException {
    ImmutableSet<BuildTarget> requiredBuildTargets =
        writeProjectAndGetRequiredBuildTargets(targetGraphAndTargets);

    if (requiredBuildTargets.isEmpty()) {
      return ExitCode.SUCCESS;
    }

    if (projectConfig.isSkipBuildEnabled()) {
      ConsoleEvent.severe(
          "Please remember to buck build --deep the targets you intent to work with.");
      return ExitCode.SUCCESS;
    }

    return projectGeneratorParameters.isProcessAnnotations()
        ? buildRequiredTargetsWithoutUsingCacheForAnnotatedTargets(
            targetGraphAndTargets, requiredBuildTargets)
        : runBuild(requiredBuildTargets);
  }

  private ImmutableSet<BuildTarget> writeProjectAndGetRequiredBuildTargets(
      TargetGraphAndTargets targetGraphAndTargets) throws IOException {
    ActionGraphAndResolver result =
        Preconditions.checkNotNull(getActionGraph(targetGraphAndTargets.getTargetGraph()));

    BuildRuleResolver ruleResolver = result.getResolver();

    JavacOptions javacOptions = buckConfig.getView(JavaBuckConfig.class).getDefaultJavacOptions();

    IjProject project =
        new IjProject(
            targetGraphAndTargets,
            getJavaPackageFinder(buckConfig),
            JavaFileParser.createJavaFileParser(javacOptions),
            ruleResolver,
            cell.getFilesystem(),
            projectConfig);

    return project.write();
  }

  private ExitCode buildRequiredTargetsWithoutUsingCacheForAnnotatedTargets(
      TargetGraphAndTargets targetGraphAndTargets, ImmutableSet<BuildTarget> requiredBuildTargets)
      throws IOException, InterruptedException {
    ImmutableSet<BuildTarget> annotatedTargets =
        getTargetsWithAnnotations(targetGraphAndTargets.getTargetGraph(), requiredBuildTargets);

    ImmutableSet<BuildTarget> unannotatedTargets =
        Sets.difference(requiredBuildTargets, annotatedTargets).immutableCopy();

    ExitCode exitCode = runBuild(unannotatedTargets);
    if (exitCode != ExitCode.SUCCESS) {
      addBuildFailureError();
    }

    if (annotatedTargets.isEmpty()) {
      return exitCode;
    }

    ExitCode annotationExitCode = buckBuildRunner.runBuild(annotatedTargets, true);
    if (exitCode == ExitCode.SUCCESS && annotationExitCode != ExitCode.SUCCESS) {
      addBuildFailureError();
    }

    return exitCode == ExitCode.SUCCESS ? annotationExitCode : exitCode;
  }

  private ExitCode runBuild(ImmutableSet<BuildTarget> targets)
      throws IOException, InterruptedException {
    return buckBuildRunner.runBuild(targets, false);
  }

  private ImmutableSet<BuildTarget> getTargetsWithAnnotations(
      final TargetGraph targetGraph, ImmutableSet<BuildTarget> buildTargets) {
    return buildTargets
        .stream()
        .filter(
            input -> {
              TargetNode<?, ?> targetNode = targetGraph.get(input);
              return targetNode != null && isTargetWithAnnotations(targetNode);
            })
        .collect(ImmutableSet.toImmutableSet());
  }

  private void addBuildFailureError() {
    console
        .getAnsi()
        .printHighlightedSuccessText(
            console.getStdErr(),
            "Because the build did not complete successfully some parts of the project may not\n"
                + "work correctly with IntelliJ. Please fix the errors and run this command again.\n");
  }

  private static boolean isTargetWithAnnotations(TargetNode<?, ?> target) {
    if (target.getDescription() instanceof JavaLibraryDescription) {
      return false;
    }
    JavaLibraryDescriptionArg arg = ((JavaLibraryDescriptionArg) target.getConstructorArg());
    return !arg.getAnnotationProcessors().isEmpty();
  }

  public JavaPackageFinder getJavaPackageFinder(BuckConfig buckConfig) {
    return buckConfig.getView(JavaBuckConfig.class).createDefaultJavaPackageFinder();
  }

  private ProjectTestsMode testsMode() {
    ProjectTestsMode parameterMode = ProjectTestsMode.WITH_TESTS;

    // TODO(shemitz) Just refactoring the existing incoherence ... really need to clean this up
    if (projectGeneratorParameters.isWithoutTests()) {
      parameterMode = ProjectTestsMode.WITHOUT_TESTS;
    } else if (projectGeneratorParameters.isWithoutDependenciesTests()) {
      parameterMode = ProjectTestsMode.WITHOUT_DEPENDENCIES_TESTS;
    } else if (projectGeneratorParameters.isWithTests()) {
      parameterMode = ProjectTestsMode.WITH_TESTS;
    }

    return parameterMode;
  }

  private boolean isWithTests() {
    return testsMode() != ProjectTestsMode.WITHOUT_TESTS;
  }

  private boolean isWithDependenciesTests() {
    return testsMode() == ProjectTestsMode.WITH_TESTS;
  }

  private TargetGraphAndTargets createTargetGraph(
      TargetGraph projectGraph,
      ImmutableSet<BuildTarget> graphRoots,
      boolean needsFullRecursiveParse,
      ListeningExecutorService executor)
      throws IOException, InterruptedException, BuildFileParseException, BuildTargetException,
          VersionException {

    boolean isWithTests = isWithTests();
    ImmutableSet<BuildTarget> explicitTestTargets = ImmutableSet.of();

    if (needsFullRecursiveParse) {
      return TargetGraphAndTargets.create(
          graphRoots, projectGraph, isWithTests, explicitTestTargets);
    }

    if (isWithTests) {
      explicitTestTargets = getExplicitTestTargets(graphRoots, projectGraph);
      projectGraph =
          parser.buildTargetGraph(
              buckEventBus,
              cell,
              enableParserProfiling,
              executor,
              Sets.union(graphRoots, explicitTestTargets));
    }

    TargetGraphAndTargets targetGraphAndTargets =
        TargetGraphAndTargets.create(graphRoots, projectGraph, isWithTests, explicitTestTargets);
    if (buckConfig.getBuildVersions()) {
      targetGraphAndTargets =
          TargetGraphAndTargets.toVersionedTargetGraphAndTargets(
              targetGraphAndTargets,
              versionedTargetGraphCache,
              buckEventBus,
              buckConfig,
              typeCoercerFactory,
              explicitTestTargets);
    }
    return targetGraphAndTargets;
  }

  /**
   * @param buildTargets The set of targets for which we would like to find tests
   * @param projectGraph A TargetGraph containing all nodes and their tests.
   * @return A set of all test targets that test any of {@code buildTargets} or their dependencies.
   */
  private ImmutableSet<BuildTarget> getExplicitTestTargets(
      ImmutableSet<BuildTarget> buildTargets, TargetGraph projectGraph) {
    Iterable<TargetNode<?, ?>> projectRoots = projectGraph.getAll(buildTargets);
    Iterable<TargetNode<?, ?>> nodes;
    if (isWithDependenciesTests()) {
      nodes = projectGraph.getSubgraph(projectRoots).getNodes();
    } else {
      nodes = projectRoots;
    }
    return TargetGraphAndTargets.getExplicitTestTargets(nodes.iterator());
  }
}
