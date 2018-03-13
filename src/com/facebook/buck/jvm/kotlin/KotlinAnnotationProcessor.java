package com.facebook.buck.jvm.kotlin;

import static com.facebook.buck.jvm.java.Javac.SRC_ZIP;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.CompileAgainstLibraryType;
import com.facebook.buck.jvm.java.DefaultJavaLibraryClasspaths;
import com.facebook.buck.jvm.java.JavaLibraryDeps;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildDeps;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.CopyStep.DirectoryMode;
import com.facebook.buck.util.zip.ZipCompressionLevel;
import com.facebook.buck.zip.ZipStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedSet;
import javax.annotation.Nullable;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class KotlinAnnotationProcessor extends AbstractBuildRule {

  @AddToRuleKey(stringify = true)
  private final Path output;
  @AddToRuleKey
  private final KotlinAnnotationProcessorStepFactory stepFactory;
  @AddToRuleKey
  private final ImmutableSortedSet<SourcePath> srcs;

  // The below fields doesn't need to be added to rule key, as they're temporary
  // We would probably want to add incremental data, but as we're not using it
  // it's not add at this moment
  private final Optional<String> sourcesPath;
  private final Optional<String> classesPath;
  private final Optional<String> stubsPath;
  private final Optional<String> incrementalDataPath;

  private final BuildRuleParams buildRuleParams;
  private final BuildRuleResolver buildRuleResolver;
  private final KotlinAnnotationProcessorDescriptionArg args;
  private final DefaultJavaLibraryClasspaths classpaths;

  KotlinAnnotationProcessor(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      KotlinAnnotationProcessorStepFactory stepFactory,
      BuildRuleParams buildRuleParams,
      BuildRuleResolver buildRuleResolver,
      KotlinAnnotationProcessorDescriptionArg args) {
    super(buildTarget, projectFilesystem);
    this.stepFactory = stepFactory;
    this.args = args;
    this.srcs = args.getSrcs();
    this.buildRuleParams = buildRuleParams;
    this.buildRuleResolver = buildRuleResolver;
    this.classpaths = getClasspaths();
    this.output = BuildTargets.getGenPath(projectFilesystem, getBuildTarget(), "gen-sources__%s/generated" + SRC_ZIP);
    this.sourcesPath = args.getGeneratedSourcesPath();
    this.classesPath = args.getGeneratedClassesPath();
    this.stubsPath = args.getGeneratedStubsPath();
    this.incrementalDataPath = args.getGeneratedIncrementalDataPath();
  }

  private DefaultJavaLibraryClasspaths getClasspaths() {
    return DefaultJavaLibraryClasspaths.builder(buildRuleResolver)
        .setBuildRuleParams(buildRuleParams)
        .setConfiguredCompiler(stepFactory)
        .setDeps(Preconditions.checkNotNull(JavaLibraryDeps.newInstance(args, buildRuleResolver)))
        .setCompileAgainstLibraryType(CompileAgainstLibraryType.FULL) // No abi for kt yet
        .build();
  }

  @Override
  public SortedSet<BuildRule> getBuildDeps() {
    return new BuildDeps(ImmutableSortedSet.<BuildRule>naturalOrder()
        .addAll(classpaths.getNonClasspathDeps())
        .addAll(stepFactory.getBuildDeps(new SourcePathRuleFinder(buildRuleResolver)))
        .addAll(classpaths.getCompileTimeClasspathAbiDeps())
        .addAll(classpaths.getCompileTimeClasspathFullDeps())
        .build());
  }

  @Override
  public ImmutableList<? extends Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    ProjectFilesystem filesystem = getProjectFilesystem();
    BuildTarget target = getBuildTarget();

    AnnotationProcessorParameters apParameters =
        AnnotationProcessorParameters.builder()
            .setClasspathEntriesSourcePaths(classpaths.getCompileTimeClasspathSourcePaths(), context.getSourcePathResolver())
            .setOptionMaps(target, filesystem, args.getAnnotationProcessorOptions(), args.getJavacArguments())
            .setSourceFileSourcePaths(srcs, filesystem, context.getSourcePathResolver())
            .setScratchPaths(target, filesystem, sourcesPath, classesPath, stubsPath, incrementalDataPath)
            .build();

    stepFactory.createAnnotationProcessorSteps(
        context,
        target,
        apParameters,
        steps,
        buildableContext
    );


    Path tmpFolder = BuildTargets.getScratchPath(filesystem, target, "gen-sources__%s");
    steps.add(CopyStep.forDirectory(filesystem, apParameters.getSourcesPath(), tmpFolder, DirectoryMode.CONTENTS_ONLY));
    steps.add(CopyStep.forDirectory(filesystem, apParameters.getClassesPath(), tmpFolder, DirectoryMode.CONTENTS_ONLY));
    steps.add(CopyStep.forDirectory(filesystem, apParameters.getSourcesPath(), tmpFolder, DirectoryMode.CONTENTS_ONLY));

    Path outputFolder = BuildTargets.getGenPath(filesystem, target, "gen-sources__%s");
    stepFactory.addCreateFolderStep(steps, filesystem, buildableContext, context, outputFolder);
    steps.add(
        new ZipStep(
            filesystem,
            output,
            ImmutableSet.of(),
            false,
            ZipCompressionLevel.DEFAULT,
            tmpFolder));
    return steps.build();
  }

  @Nullable
  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
  }
}
