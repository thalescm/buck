package com.facebook.buck.jvm.kotlin;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;

import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.ConfiguredCompiler;
import com.facebook.buck.jvm.java.ExtraClasspathProvider;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.AddsToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class KotlinAnnotationProcessorStepFactory implements ConfiguredCompiler, AddsToRuleKey {

  private static final String COMPILER_BUILTINS = "-Xadd-compiler-builtins";
  private static final String LOAD_BUILTINS_FROM = "-Xload-builtins-from-dependencies";
  private static final String PLUGIN = "-P";
  private static final String APT_MODE = "aptMode=";
  private static final String X_PLUGIN_ARG = "-Xplugin=";
  private static final String KAPT3_PLUGIN = "plugin:org.jetbrains.kotlin.kapt3:";
  private static final String AP_CLASSPATH_ARG = KAPT3_PLUGIN + "apclasspath=";
  // output path for generated sources;
  private static final String SOURCES_ARG = KAPT3_PLUGIN + "sources=";
  private static final String CLASSES_ARG = KAPT3_PLUGIN + "classes=";
  private static final String INCREMENTAL_ARG = KAPT3_PLUGIN + "incrementalData=";
  // output path for java stubs;
  private static final String STUBS_ARG = KAPT3_PLUGIN + "stubs=";
  private static final String LIGHT_ANALYSIS = KAPT3_PLUGIN + "useLightAnalysis=";
  private static final String CORRECT_ERROR_TYPES = KAPT3_PLUGIN + "correctErrorTypes=";
  private static final String VERBOSE_ARG = KAPT3_PLUGIN + "verbose=";
  private static final String JAVAC_ARG = KAPT3_PLUGIN + "javacArguments=";
  private static final String AP_OPTIONS = KAPT3_PLUGIN + "apoptions=";
//  private static final String MAP_DIAGNOSTIC_LOCATIONS = KAPT3_PLUGIN + "mapDiagnosticLocations=";
  private static final String KAPT_GENERATED = "kapt.kotlin.generated";
  private static final String MODULE_NAME = "-module-name";

  @AddToRuleKey private final Kotlinc kotlinc;
  @AddToRuleKey private final ImmutableList<String> extraArguments;
  @AddToRuleKey private final ExtraClasspathProvider extraClassPath;
  private final ImmutableSortedSet<Path> kotlinHomeLibraries;
  private final ProjectFilesystem projectFilesystem;

  KotlinAnnotationProcessorStepFactory(
      ProjectFilesystem projectFilesystem,
      Kotlinc kotlinc,
      ImmutableSortedSet<Path> kotlinHomeLibraries,
      ImmutableList<String> extraArguments,
      ExtraClasspathProvider extraClassPath) {
    this.projectFilesystem = projectFilesystem;
    this.kotlinc = kotlinc;
    this.kotlinHomeLibraries = kotlinHomeLibraries;
    this.extraArguments = extraArguments;
    this.extraClassPath = extraClassPath;
  }

  public Path getGenFilesDir(BuildTarget invokingRule) {
    return BuildTargets
        .getAnnotationPath(projectFilesystem, invokingRule, "%s_kapt__gen");
  }

  public void createAnnotationProcessorSteps(
      BuildContext context,
      BuildTarget invokingRule,
      AnnotationProcessorParameters parameters,
      Builder<Step> steps,
      BuildableContext buildableContext) {

    ImmutableSortedSet<Path> declaredClasspathEntries = parameters.getClasspathEntries();
    ImmutableSortedSet<Path> sourceFilePaths = parameters.getSourceFilePaths();
    Path pathToSrcsList = parameters.getPathToSourcesList();

    // Only invoke kotlinc if we have kotlin files.
    if (sourceFilePaths.stream().anyMatch(PathMatchers.KOTLIN_PATH_MATCHER::matches)) {

      Path stubsOutput = BuildTargets
          .getAnnotationPath(projectFilesystem, invokingRule, "%s_kapt__stubs");
      Path incrementalData =
          BuildTargets.getAnnotationPath(projectFilesystem, invokingRule, "%s_kapt__incremental_data");
      Path genOutput = getGenFilesDir(invokingRule);

      addCreateFolderStep(steps, projectFilesystem, buildableContext, context, stubsOutput);
      addCreateFolderStep(steps, projectFilesystem, buildableContext, context, incrementalData);
      addCreateFolderStep(steps, projectFilesystem, buildableContext, context, genOutput);

      ImmutableSortedSet<Path> allClasspaths = ImmutableSortedSet.<Path>naturalOrder()
          .addAll(
              Optional.ofNullable(extraClassPath.getExtraClasspath())
                  .orElse(ImmutableList.of()))
          .addAll(declaredClasspathEntries)
          .addAll(kotlinHomeLibraries)
          .build();

      addAnnotationProcessingSteps(
          invokingRule,
          steps,
          projectFilesystem,
          sourceFilePaths,
          pathToSrcsList,
          sourceFilePaths,
          allClasspaths,
          extraArguments,
          genOutput,
          stubsOutput,
          incrementalData);
    }
  }

  private void addAnnotationProcessingSteps(
      BuildTarget invokingRule,
      ImmutableList.Builder<Step> steps,
      ProjectFilesystem filesystem,
      ImmutableSortedSet<Path> sourceFilePaths,
      Path pathToSrcsList,
      ImmutableSortedSet<Path> sourcePaths,
      Iterable<? extends Path> declaredClasspathEntries,
      ImmutableList<String> extraArguments,
      Path genOutput,
      Path stubsOutput,
      Path incrementalData) {

    ImmutableList<String> apClassPaths =
        ImmutableList.<String>builder()
            .add(AP_CLASSPATH_ARG + kotlinc.getAnnotationProcessorPath())
            .add(AP_CLASSPATH_ARG + kotlinc.getStdlibPath())
            .add(SOURCES_ARG + filesystem.resolve(genOutput))
            .add(CLASSES_ARG + filesystem.resolve(genOutput))
            .add(INCREMENTAL_ARG + filesystem.resolve(incrementalData))
            .add(STUBS_ARG + filesystem.resolve(stubsOutput))
            .add(
                AP_OPTIONS
                    + encodeOptions(
                    Collections.singletonMap(KAPT_GENERATED, genOutput.toString())))
            .add(JAVAC_ARG + encodeOptions(Collections.emptyMap()))
            .add(LIGHT_ANALYSIS + "true")
            .add(VERBOSE_ARG + "true")
            .add(CORRECT_ERROR_TYPES + "false") // TODO: Provide value as argument
            // .add(MAP_DIAGNOSTIC_LOCATIONS + "true") // TODO: Provide value as argument,
                                                       // can only be accepted if kotlin version
                                                       // is greater then 1.2.30
            .build();
    String join = Joiner.on(",").join(apClassPaths);

    // First generate java stubs
    steps.add(
        new KotlincStep(
            invokingRule,
            null,
            sourceFilePaths,
            pathToSrcsList,
            ImmutableSortedSet.<Path>naturalOrder()
                .add(kotlinc.getStdlibPath())
                .addAll(declaredClasspathEntries)
                .build(),
            kotlinc,
            ImmutableList.<String>builder()
                .addAll(extraArguments)
                .add(MODULE_NAME)
                .add(invokingRule.getShortNameAndFlavorPostfix())
                .add(COMPILER_BUILTINS)
                .add(LOAD_BUILTINS_FROM)
                .add(PLUGIN)
                .add(KAPT3_PLUGIN + APT_MODE + "stubs," + join)
                .add(X_PLUGIN_ARG + kotlinc.getAnnotationProcessorPath())
                .build(),
            filesystem,
            Optional.empty()));

    // Then run the annotation processor
    steps.add(
        new KotlincStep(
            invokingRule,
            null,
            sourcePaths,
            pathToSrcsList,
            ImmutableSortedSet.<Path>naturalOrder()
                .add(kotlinc.getStdlibPath())
                .addAll(declaredClasspathEntries)
                .build(),
            kotlinc,
            ImmutableList.<String>builder()
                .addAll(extraArguments)
                .add(MODULE_NAME)
                .add(invokingRule.getShortNameAndFlavorPostfix())
                .add(COMPILER_BUILTINS)
                .add(LOAD_BUILTINS_FROM)
                .add(PLUGIN)
                .add(KAPT3_PLUGIN + APT_MODE + "apt," + join)
                .add(X_PLUGIN_ARG + kotlinc.getAnnotationProcessorPath())
                .build(),
            filesystem,
            Optional.empty()));
  }

  public void addCreateFolderStep(
      ImmutableList.Builder<Step> steps,
      ProjectFilesystem filesystem,
      BuildableContext buildableContext,
      BuildContext context,
      Path location) {
    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), filesystem, location)));
    buildableContext.recordArtifact(location);
  }

  private String encodeOptions(Map<String, String> options) {
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(os);

      oos.writeInt(options.size());
      for (Map.Entry<String, String> entry : options.entrySet()) {
        oos.writeUTF(entry.getKey());
        oos.writeUTF(entry.getValue());
      }

      oos.flush();
      return printBase64Binary(os.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Tool getCompiler() {
    return kotlinc;
  }
}
