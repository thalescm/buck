package com.facebook.buck.jvm.kotlin;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.ExtraClasspathProvider;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleCreationContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.Description;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.immutables.value.Value;

public class KotlinAnnotationProcessorDescription
  implements Description<KotlinAnnotationProcessorDescriptionArg> {

  private final KotlinBuckConfig kotlinBuckConfig;

  KotlinAnnotationProcessorDescription(KotlinBuckConfig kotlinBuckConfig) {
    this.kotlinBuckConfig = kotlinBuckConfig;
  }

  @Override
  public Class<KotlinAnnotationProcessorDescriptionArg> getConstructorArgType() {
    return KotlinAnnotationProcessorDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContext context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      KotlinAnnotationProcessorDescriptionArg args) {

    ProjectFilesystem projectFilesystem = context.getProjectFilesystem();

    KotlinAnnotationProcessorStepFactory factory = new KotlinAnnotationProcessorStepFactory(
        projectFilesystem,
        kotlinBuckConfig.getKotlinc(),
        kotlinBuckConfig.getKotlinHomeLibraries(),
        args.getExtraArguments(),
        ExtraClasspathProvider.EMPTY
    );

    return new KotlinAnnotationProcessor(
        buildTarget,
        projectFilesystem,
        factory,
        params,
        context.getBuildRuleResolver(),
        args);
  }

  public interface CoreArg
      extends KotlinLibraryDescription.CoreArg {
    Optional<String> getGeneratedSourcesPath();
    Optional<String> getGeneratedClassesPath();
    Optional<String> getGeneratedStubsPath();
    Optional<String> getGeneratedIncrementalDataPath();
    ImmutableMap<String, String> getAnnotationProcessorOptions();
    ImmutableMap<String, String> getJavacArguments();
    Optional<Boolean> getCorrectErrorTypes();
    Optional<Boolean> getMapDiagnosticLocations();
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractKotlinAnnotationProcessorDescriptionArg
      extends KotlinAnnotationProcessorDescription.CoreArg {}
}
