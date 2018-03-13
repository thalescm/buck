package com.facebook.buck.jvm.kotlin;


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
import java.util.Map;
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

  public abstract Path getWorkingDirectory();

  public abstract Path getPathToSourcesList();

  public abstract Path getSourcesPath();
  public abstract Path getClassesPath();
  public abstract Path getStubsPath();
  public abstract Path getIncrementalDataPath();
  public abstract Path getKaptGeneratedPath();

  @Value.Default
  public ImmutableMap<String, String> getAnnotationProcessorOptions() { return ImmutableMap.of(); }

  @Value.Default
  public ImmutableMap<String, String> getJavacArguments() { return ImmutableMap.of(); }

  public abstract static class Builder {
    public AnnotationProcessorParameters.Builder setScratchPaths(
        BuildTarget target,
        ProjectFilesystem projectFilesystem,
        Optional<String> sourcesPath,
        Optional<String> classesPath,
        Optional<String> stubsPath,
        Optional<String> incrementalDataPath,
        Optional<String> kaptGeneratedPath) {
      AnnotationProcessorParameters.Builder builder = (AnnotationProcessorParameters.Builder) this;

      return builder
          .setSourcesPath(BuildTargets.getAnnotationPath(projectFilesystem, target, sourcesPath.orElse("%s_kapt__sources")))
          .setClassesPath(BuildTargets.getAnnotationPath(projectFilesystem, target, classesPath.orElse("%s_kapt__classes")))
          .setStubsPath(BuildTargets.getAnnotationPath(projectFilesystem, target, stubsPath.orElse("%s_kapt__stubs")))
          .setKaptGeneratedPath(BuildTargets.getAnnotationPath(projectFilesystem, target, kaptGeneratedPath.orElse("%s_kapt__generated")))
          .setIncrementalDataPath(BuildTargets.getAnnotationPath(projectFilesystem, target, incrementalDataPath.orElse("%s_kapt__incremental_data")))
          .setPathToSourcesList(BuildTargets.getGenPath(projectFilesystem, target, "%s_kapt__srcs"))
          .setWorkingDirectory(BuildTargets.getGenPath(projectFilesystem, target, "%s_kapt__working_directory"));
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
  }
}
