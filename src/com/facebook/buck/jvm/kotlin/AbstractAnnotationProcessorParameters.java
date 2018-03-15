package com.facebook.buck.jvm.kotlin;


import static com.facebook.buck.jvm.kotlin.KotlinAnnotationProcessorStepFactory.KAPT_GENERATED;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
public abstract class AbstractAnnotationProcessorParameters {

  @Value.Default
  public ImmutableSortedSet<Path> getSourceFilePaths() {
    return ImmutableSortedSet.of();
  }

  @Value.Default
  public ImmutableSortedSet<Path> getClasspathEntries() {
    return ImmutableSortedSet.of();
  }

  @Value.Default
  public ImmutableSortedSet<Path> getAnnotationProcessorClasspathEntries() {
    return ImmutableSortedSet.of();
  }

  public abstract Path getWorkingDirectory();

  public abstract Path getPathToSourcesList();

  public abstract Path getSourcesPath();
  public abstract Path getClassesPath();
  public abstract Path getStubsPath();
  public abstract Path getIncrementalDataPath();
  public abstract Path getKaptGeneratedPath();
  public abstract ImmutableMap<String, String> getAnnotationProcessorOptions();
  public abstract ImmutableMap<String, String> getJavacArguments();

  public abstract static class Builder {

    private Path resolvePath(
        ProjectFilesystem projectFilesystem,
        BuildTarget target,
        Optional<String> intended,
        String fallback) {
      if (intended.isPresent()) {
        return projectFilesystem.resolve(intended.get());
      } else  {
        return BuildTargets.getAnnotationPath(projectFilesystem, target, fallback);
      }
    }

    public AnnotationProcessorParameters.Builder setScratchPaths(
        BuildTarget target,
        ProjectFilesystem projectFilesystem,
        Optional<String> sourcesPath,
        Optional<String> classesPath,
        Optional<String> stubsPath,
        Optional<String> incrementalDataPath) {

      return ((AnnotationProcessorParameters.Builder) this)
          .setSourcesPath(resolvePath(projectFilesystem, target, sourcesPath, "%s_kapt__sources"))
          .setClassesPath(resolvePath(projectFilesystem, target, classesPath, "%s_kapt__classes"))
          .setStubsPath(resolvePath(projectFilesystem, target, stubsPath, "%s_kapt__stubs"))
          .setIncrementalDataPath(resolvePath(projectFilesystem, target, incrementalDataPath, "%s_kapt__incremental_data"))
          .setPathToSourcesList(BuildTargets.getGenPath(projectFilesystem, target, "%s_kapt__srcs"))
          .setWorkingDirectory(BuildTargets.getGenPath(projectFilesystem, target, "%s_kapt__working_directory"));
    }

    public AnnotationProcessorParameters.Builder setOptionMaps(
        BuildTarget target,
        ProjectFilesystem projectFilesystem,
        ImmutableMap<String, String> annotationProcessorOptions,
        ImmutableMap<String, String> javacArguments) {

      Path kaptGeneratedPath;
      if (!annotationProcessorOptions.containsKey(KAPT_GENERATED)) {
        kaptGeneratedPath = BuildTargets.getAnnotationPath(projectFilesystem, target, "%s_kapt__generated");
        annotationProcessorOptions = ImmutableMap.<String,String>builder()
            .putAll(annotationProcessorOptions)
            .put(KAPT_GENERATED, projectFilesystem.resolve(kaptGeneratedPath).toString())
            .build();
      } else {
        kaptGeneratedPath = projectFilesystem.resolve(annotationProcessorOptions.get(KAPT_GENERATED));
      }

      return ((AnnotationProcessorParameters.Builder) this)
          .setKaptGeneratedPath(kaptGeneratedPath)
          .setAnnotationProcessorOptions(annotationProcessorOptions)
          .setJavacArguments(javacArguments);
    }

    public AnnotationProcessorParameters.Builder setSourceFileSourcePaths(
        ImmutableSortedSet<SourcePath> srcs,
        ProjectFilesystem projectFilesystem,
        SourcePathResolver resolver) {
      ImmutableSortedSet<Path> kotlinSrcs =
          srcs.stream()
              .map(src -> projectFilesystem.relativize(resolver.getAbsolutePath(src)))
              .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
      return ((AnnotationProcessorParameters.Builder) this).setSourceFilePaths(kotlinSrcs);
    }

    public AnnotationProcessorParameters.Builder setClasspathEntriesSourcePaths(
        ImmutableSortedSet<SourcePath> compileTimeClasspathSourcePaths,
        SourcePathResolver resolver) {
      ImmutableSortedSet<Path> compileTimeClasspathPaths =
          compileTimeClasspathSourcePaths
              .stream()
              .map(resolver::getAbsolutePath)
              .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
      return ((AnnotationProcessorParameters.Builder) this).setClasspathEntries(compileTimeClasspathPaths);
    }

    public AnnotationProcessorParameters.Builder setAnnotationProcessorClasspathEntriesSourcePaths(
        ImmutableSortedSet<SourcePath> compileTimeClasspathSourcePaths,
        SourcePathResolver resolver) {
      ImmutableSortedSet<Path> compileTimeClasspathPaths =
          compileTimeClasspathSourcePaths
              .stream()
              .map(resolver::getAbsolutePath)
              .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
      return ((AnnotationProcessorParameters.Builder) this).setAnnotationProcessorClasspathEntries(compileTimeClasspathPaths);
    }
  }
}
