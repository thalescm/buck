package com.facebook.buck.jvm.kotlin;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;

import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.ConfiguredCompiler;
import com.facebook.buck.jvm.java.ExtraClasspathProvider;
import com.facebook.buck.model.BuildTarget;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
  static final String KAPT_GENERATED = "kapt.kotlin.generated";
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

  public void createAnnotationProcessorSteps(
      BuildContext context,
      BuildTarget invokingRule,
      AnnotationProcessorParameters parameters,
      Builder<Step> steps,
      BuildableContext buildableContext) {

    ImmutableSortedSet<Path> declaredClasspathEntries = parameters.getClasspathEntries();
    ImmutableSortedSet<Path> declaredAnnotationProcessorClasspathEntries = parameters
        .getAnnotationProcessorClasspathEntries();
    ImmutableSortedSet<Path> sourceFilePaths = parameters.getSourceFilePaths();
    Path pathToSrcsList = parameters.getPathToSourcesList();

    Path stubsOutput = parameters.getStubsPath();
    Path incrementalData = parameters.getIncrementalDataPath();
    Path classes = parameters.getClassesPath();
    Path sources = parameters.getSourcesPath();
    Path kaptGen = parameters.getKaptGeneratedPath();

    addCreateFolderStep(steps, projectFilesystem, buildableContext, context, stubsOutput);
    addCreateFolderStep(steps, projectFilesystem, buildableContext, context, incrementalData);
    addCreateFolderStep(steps, projectFilesystem, buildableContext, context, classes);
    addCreateFolderStep(steps, projectFilesystem, buildableContext, context, sources);
    addCreateFolderStep(steps, projectFilesystem, buildableContext, context, kaptGen);

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
        pathToSrcsList,
        sourceFilePaths,
        allClasspaths,
        declaredAnnotationProcessorClasspathEntries,
        extraArguments,
        classes,
        sources,
        stubsOutput,
        incrementalData,
        parameters.getAnnotationProcessorOptions(),
        parameters.getJavacArguments());
  }

  private void addAnnotationProcessingSteps(
      BuildTarget invokingRule,
      ImmutableList.Builder<Step> steps,
      ProjectFilesystem filesystem,
      Path pathToSrcsList,
      ImmutableSortedSet<Path> sourcePaths,
      ImmutableSortedSet<Path> declaredClasspathEntries,
      ImmutableSortedSet<Path> declaredAnnotationProcessorClasspathEntries,
      ImmutableList<String> extraArguments,
      Path classes,
      Path sources,
      Path stubsOutput,
      Path incrementalData,
      ImmutableMap<String, String> apOptions,
      ImmutableMap<String, String> javacArguments) {

    ImmutableList<String> annotationProcessorArguments =
        ImmutableList.<String>builder()
            .add(AP_CLASSPATH_ARG + kotlinc.getAnnotationProcessorPath())
            .add(AP_CLASSPATH_ARG + kotlinc.getStdlibPath())
            .addAll(declaredAnnotationProcessorClasspathEntries
                .stream().map(path -> AP_CLASSPATH_ARG + filesystem.resolve(path))
                .collect(Collectors.toSet()))
            .add(SOURCES_ARG + filesystem.resolve(sources))
            .add(CLASSES_ARG + filesystem.resolve(classes))
            .add(INCREMENTAL_ARG + filesystem.resolve(incrementalData))
            .add(STUBS_ARG + filesystem.resolve(stubsOutput))
            .add(AP_OPTIONS + encodeOptions(apOptions))
            .add(JAVAC_ARG + encodeOptions(javacArguments))
            .add(LIGHT_ANALYSIS + "true")
            .add(VERBOSE_ARG + "true")
            .add(CORRECT_ERROR_TYPES + "false") // TODO: Provide value as argument
            // .add(MAP_DIAGNOSTIC_LOCATIONS + "true") // TODO: Provide value as argument,
            // can only be accepted if kotlin version
            // is greater then 1.2.30
            .build();
    String join = Joiner.on(",").join(annotationProcessorArguments);

    // First generate java stubs
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
