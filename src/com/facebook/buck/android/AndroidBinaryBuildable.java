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

package com.facebook.buck.android;

import com.facebook.buck.android.apkmodule.APKModule;
import com.facebook.buck.android.exopackage.ExopackageMode;
import com.facebook.buck.android.redex.ReDexStep;
import com.facebook.buck.android.redex.RedexOptions;
import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.XzStep;
import com.facebook.buck.unarchive.UnzipStep;
import com.facebook.buck.util.MoreSuppliers;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.zip.RepackZipEntriesStep;
import com.facebook.buck.zip.ZipScrubberStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

class AndroidBinaryBuildable implements AddsToRuleKey {

  /**
   * This is the path from the root of the APK that should contain the metadata.txt and
   * secondary-N.dex.jar files for secondary dexes.
   */
  static final String SMART_DEX_SECONDARY_DEX_SUBDIR =
      "assets/smart-dex-secondary-program-dex-jars";

  @AddToRuleKey private final EnumSet<ExopackageMode> exopackageModes;

  @AddToRuleKey private final SourcePath androidManifestPath;

  @AddToRuleKey private final DexFilesInfo dexFilesInfo;
  @AddToRuleKey private final NativeFilesInfo nativeFilesInfo;
  @AddToRuleKey private final ResourceFilesInfo resourceFilesInfo;

  @AddToRuleKey final boolean packageAssetLibraries;
  @AddToRuleKey final boolean compressAssetLibraries;
  @AddToRuleKey final Optional<CompressionAlgorithm> assetCompressionAlgorithm;

  @AddToRuleKey private final Optional<RedexOptions> redexOptions;

  @AddToRuleKey
  // Redex accesses some files that are indirectly referenced through the proguard command-line.txt.
  // TODO(cjhopman): Redex shouldn't do that, or this list should be constructed more carefully.
  private final ImmutableList<SourcePath> additionalRedexInputs;

  @AddToRuleKey private final OptionalInt xzCompressionLevel;

  @AddToRuleKey private final SourcePath keystorePath;
  @AddToRuleKey private final SourcePath keystorePropertiesPath;

  @AddToRuleKey private final ImmutableSortedSet<APKModule> apkModules;

  // The java launcher is used for ApkBuilder.
  @AddToRuleKey private final Tool javaRuntimeLauncher;

  // Post-process resource compression
  @AddToRuleKey private final boolean isCompressResources;

  @AddToRuleKey private final ImmutableMap<APKModule, SourcePath> moduleResourceApkPaths;

  private final boolean isApk;

  // These should be the only things not added to the rulekey.
  private final ProjectFilesystem filesystem;
  private final BuildTarget buildTarget;
  private final AndroidSdkLocation androidSdkLocation;
  private final AndroidPlatformTarget androidPlatformTarget;

  AndroidBinaryBuildable(
      BuildTarget buildTarget,
      ProjectFilesystem filesystem,
      AndroidSdkLocation androidSdkLocation,
      AndroidPlatformTarget androidPlatformTarget,
      SourcePath keystorePath,
      SourcePath keystorePropertiesPath,
      Optional<RedexOptions> redexOptions,
      ImmutableList<SourcePath> additionalRedexInputs,
      EnumSet<ExopackageMode> exopackageModes,
      OptionalInt xzCompressionLevel,
      boolean packageAssetLibraries,
      boolean compressAssetLibraries,
      Optional<CompressionAlgorithm> assetCompressionAlgorithm,
      Tool javaRuntimeLauncher,
      SourcePath androidManifestPath,
      boolean isCompressResources,
      DexFilesInfo dexFilesInfo,
      NativeFilesInfo nativeFilesInfo,
      ResourceFilesInfo resourceFilesInfo,
      ImmutableSortedSet<APKModule> apkModules,
      ImmutableMap<APKModule, SourcePath> moduleResourceApkPaths,
      boolean isApk) {
    this.filesystem = filesystem;
    this.buildTarget = buildTarget;
    this.androidSdkLocation = androidSdkLocation;
    this.androidPlatformTarget = androidPlatformTarget;
    this.keystorePath = keystorePath;
    this.keystorePropertiesPath = keystorePropertiesPath;
    this.redexOptions = redexOptions;
    this.additionalRedexInputs = additionalRedexInputs;
    this.exopackageModes = exopackageModes;
    this.xzCompressionLevel = xzCompressionLevel;
    this.javaRuntimeLauncher = javaRuntimeLauncher;
    this.androidManifestPath = androidManifestPath;
    this.isCompressResources = isCompressResources;
    this.apkModules = apkModules;
    this.moduleResourceApkPaths = moduleResourceApkPaths;
    this.dexFilesInfo = dexFilesInfo;
    this.nativeFilesInfo = nativeFilesInfo;
    this.packageAssetLibraries = packageAssetLibraries;
    this.compressAssetLibraries = compressAssetLibraries;
    this.assetCompressionAlgorithm = assetCompressionAlgorithm;
    this.resourceFilesInfo = resourceFilesInfo;
    this.isApk = isApk;
  }

  @SuppressWarnings("PMD.PrematureDeclaration")
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    SourcePathResolver pathResolver = context.getSourcePathResolver();
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    // The `HasInstallableApk` interface needs access to the manifest, so make sure we create our
    // own copy of this so that we don't have a runtime dep on the `AaptPackageResources` step.
    Path manifestPath = getManifestPath();
    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), manifestPath.getParent())));
    steps.add(
        CopyStep.forFile(
            getProjectFilesystem(),
            pathResolver.getRelativePath(androidManifestPath),
            manifestPath));
    buildableContext.recordArtifact(manifestPath);

    dexFilesInfo.proguardTextFilesPath.ifPresent(
        path -> {
          steps.add(createCopyProguardFilesStep(pathResolver, path));
        });

    ImmutableSet.Builder<Path> nativeLibraryDirectoriesBuilder = ImmutableSet.builder();
    // Copy the transitive closure of native-libs-as-assets to a single directory, if any.
    ImmutableSet.Builder<Path> nativeLibraryAsAssetDirectories = ImmutableSet.builder();

    ImmutableSet.Builder<Path> moduleResourcesDirectories = ImmutableSet.builder();

    ImmutableSet.Builder<ModuleInfo> modulesInfo = ImmutableSet.builder();

    ImmutableMap<String, SourcePath> mapOfModuleToSecondaryDexSourcePaths =
        dexFilesInfo.getMapOfModuleToSecondaryDexSourcePaths();

    ModuleInfo.Builder baseModuleInfo = ModuleInfo.builder();
    baseModuleInfo.setModuleName("base");

    for (APKModule module : apkModules) {
      processModule(
          module,
          nativeLibraryDirectoriesBuilder,
          nativeLibraryAsAssetDirectories,
          moduleResourcesDirectories,
          steps,
          pathResolver,
          context,
          mapOfModuleToSecondaryDexSourcePaths,
          baseModuleInfo,
          modulesInfo);
    }

    // If non-english strings are to be stored as assets, pass them to ApkBuilder.
    ImmutableSet.Builder<Path> zipFiles = ImmutableSet.builder();
    RichStream.from(resourceFilesInfo.primaryApkAssetsZips)
        .map(pathResolver::getRelativePath)
        .forEach(zipFiles::add);

    if (ExopackageMode.enabledForNativeLibraries(exopackageModes)
        && !ExopackageMode.enabledForArch64(exopackageModes)) {
      // We need to include a few dummy native libraries with our application so that Android knows
      // to run it as 32-bit.  Android defaults to 64-bit when no libraries are provided at all,
      // causing us to fail to load our 32-bit exopackage native libraries later.
      String fakeNativeLibraryBundle = System.getProperty("buck.native_exopackage_fake_path");

      if (fakeNativeLibraryBundle == null) {
        throw new RuntimeException("fake native bundle not specified in properties");
      }

      zipFiles.add(Paths.get(fakeNativeLibraryBundle));
    }

    ImmutableSet<Path> allAssetDirectories =
        ImmutableSet.<Path>builder()
            .addAll(moduleResourcesDirectories.build())
            .addAll(nativeLibraryAsAssetDirectories.build())
            .addAll(dexFilesInfo.getSecondaryDexDirs(getProjectFilesystem(), pathResolver))
            .build();

    SourcePathResolver resolver = context.getSourcePathResolver();
    Path signedApkPath = getSignedApkPath();
    Path pathToKeystore = resolver.getAbsolutePath(keystorePath);
    Supplier<KeystoreProperties> keystoreProperties =
        getKeystorePropertiesSupplier(resolver, pathToKeystore);

    ImmutableSet<Path> thirdPartyJars =
        resourceFilesInfo
            .pathsToThirdPartyJars
            .stream()
            .map(resolver::getAbsolutePath)
            .collect(ImmutableSet.toImmutableSet());
    if (isApk) {
      steps.add(
          new ApkBuilderStep(
              getProjectFilesystem(),
              pathResolver.getAbsolutePath(resourceFilesInfo.resourcesApkPath),
              getSignedApkPath(),
              pathResolver.getRelativePath(dexFilesInfo.primaryDexPath),
              allAssetDirectories,
              nativeLibraryDirectoriesBuilder.build(),
              zipFiles.build(),
              thirdPartyJars,
              pathToKeystore,
              keystoreProperties,
              false,
              javaRuntimeLauncher.getCommandPrefix(pathResolver)));
    } else {
      ImmutableSet<String> moduleNames =
          apkModules.stream().map(APKModule::getName).collect(ImmutableSet.toImmutableSet());

      for (Path path : dexFilesInfo.getSecondaryDexDirs(getProjectFilesystem(), pathResolver)) {
        if (path.getFileName().toString().equals("additional_dexes")) {
          File[] assetFiles = path.toFile().listFiles();
          if (assetFiles == null) {
            continue;
          }
          for (File assetFile : assetFiles) {
            if (!assetFile.getName().equals("assets")) {
              continue;
            }
            File[] modules = assetFile.listFiles();
            if (modules == null) {
              continue;
            }
            for (File module : modules) {
              if (moduleNames.contains(module.getName())) {
                continue;
              }
              baseModuleInfo.putAssetDirectories(module.toPath(), "assets");
            }
          }
        } else {
          baseModuleInfo.putAssetDirectories(path, "");
        }
      }

      baseModuleInfo
          .setResourceApk(pathResolver.getAbsolutePath(resourceFilesInfo.resourcesApkPath))
          .addDexFile(pathResolver.getRelativePath(dexFilesInfo.primaryDexPath))
          .setJarFilesThatMayContainResources(thirdPartyJars)
          .setZipFiles(zipFiles.build());

      modulesInfo.add(baseModuleInfo.build());

      steps.add(
          new AabBuilderStep(
              getProjectFilesystem(),
              getSignedApkPath(),
              buildTarget,
              false,
              modulesInfo.build(),
              moduleNames));
    }

    // The `ApkBuilderStep` delegates to android tools to build a ZIP with timestamps in it, making
    // the output non-deterministic.  So use an additional scrubbing step to zero these out.
    steps.add(ZipScrubberStep.of(getProjectFilesystem().resolve(signedApkPath)));

    Path apkToRedexAndAlign;
    // Optionally, compress the resources file in the .apk.
    if (isCompressResources) {
      Path compressedApkPath = getCompressedResourcesApkPath();
      apkToRedexAndAlign = compressedApkPath;
      steps.add(createRepackZipEntriesStep(signedApkPath, compressedApkPath));
    } else {
      apkToRedexAndAlign = signedApkPath;
    }

    boolean applyRedex = redexOptions.isPresent();
    Path apkToAlign = apkToRedexAndAlign;
    Path v2SignedApkPath = getFinalApkPath();

    if (applyRedex) {
      Path redexedApk = getRedexedApkPath();
      apkToAlign = redexedApk;
      steps.addAll(
          createRedexSteps(
              context,
              buildableContext,
              resolver,
              keystoreProperties,
              apkToRedexAndAlign,
              redexedApk));
    }

    if (isApk) {
      Path zipalignedApkPath = getZipalignedApkPath();
      steps.add(
          new ZipalignStep(
              getProjectFilesystem().getRootPath(),
              androidPlatformTarget,
              apkToAlign,
              zipalignedApkPath));
      steps.add(
          new ApkSignerStep(
              getProjectFilesystem(),
              zipalignedApkPath,
              v2SignedApkPath,
              keystoreProperties,
              applyRedex));

    } else {
      steps.add(
          new ZipalignStep(
              getProjectFilesystem().getRootPath(),
              androidPlatformTarget,
              apkToAlign,
              v2SignedApkPath));
    }
    buildableContext.recordArtifact(v2SignedApkPath);
    return steps.build();
  }

  private void processModule(
      APKModule module,
      ImmutableSet.Builder<Path> nativeLibraryDirectoriesBuilder,
      ImmutableSet.Builder<Path> nativeLibraryAsAssetDirectories,
      ImmutableSet.Builder<Path> moduleResourcesDirectories,
      ImmutableList.Builder<Step> steps,
      SourcePathResolver pathResolver,
      BuildContext context,
      ImmutableMap<String, SourcePath> mapOfModuleToSecondaryDexSourcePaths,
      ModuleInfo.Builder baseModuleInfo,
      ImmutableSet.Builder<ModuleInfo> modulesInfo) {
    boolean addThisModule = false;
    ImmutableMap.Builder<Path, String> assetDirectoriesBuilderForThisModule =
        ImmutableMap.builder();
    ImmutableSet.Builder<Path> nativeLibraryDirectoriesBuilderForThisModule =
        ImmutableSet.builder();
    Path resourcesDirectoryForThisModule = null;
    ImmutableSet.Builder<Path> dexFileDirectoriesBuilderForThisModule = ImmutableSet.builder();

    if (mapOfModuleToSecondaryDexSourcePaths.containsKey(module.getName())) {
      addDexFileDirectories(
          pathResolver,
          module,
          mapOfModuleToSecondaryDexSourcePaths,
          dexFileDirectoriesBuilderForThisModule,
          assetDirectoriesBuilderForThisModule);
    }

    boolean shouldPackageAssetLibraries = packageAssetLibraries || !module.isRootModule();
    if (!ExopackageMode.enabledForNativeLibraries(exopackageModes)
        && nativeFilesInfo.nativeLibsDirs.isPresent()
        && nativeFilesInfo.nativeLibsDirs.get().containsKey(module)) {
      addThisModule = true;
      addNativeDirectory(
          shouldPackageAssetLibraries,
          module,
          pathResolver,
          nativeLibraryDirectoriesBuilder,
          nativeLibraryDirectoriesBuilderForThisModule);
    }

    if (shouldPackageAssetLibraries) {
      addThisModule = true;
      addNativeLibraryAsAssetDirectory(
          module,
          context,
          nativeLibraryAsAssetDirectories,
          assetDirectoriesBuilderForThisModule,
          steps);
    }

    if (moduleResourceApkPaths.get(module) != null) {
      addThisModule = true;
      resourcesDirectoryForThisModule =
          addModuleResourceDirectory(module, context, moduleResourcesDirectories, steps);
    }

    if (!addThisModule || isApk) {
      return;
    }

    if (module.isRootModule()) {
      baseModuleInfo
          .putAllAssetDirectories(assetDirectoriesBuilderForThisModule.build())
          .addAllNativeLibraryDirectories(nativeLibraryDirectoriesBuilderForThisModule.build())
          .addAllDexFile(dexFileDirectoriesBuilderForThisModule.build());
    } else {
      String moduleName = module.getName();
      modulesInfo.add(
          ModuleInfo.of(
              moduleName,
              resourcesDirectoryForThisModule,
              dexFileDirectoriesBuilderForThisModule.build(),
              assetDirectoriesBuilderForThisModule.build(),
              nativeLibraryDirectoriesBuilderForThisModule.build(),
              ImmutableSet.<Path>builder().build(),
              ImmutableSet.<Path>builder().build()));
    }
  }

  private void addDexFileDirectories(
      SourcePathResolver pathResolver,
      APKModule module,
      ImmutableMap<String, SourcePath> mapOfModuleToSecondaryDexSourcePaths,
      ImmutableSet.Builder<Path> dexFileDirectoriesBuilderForThisModule,
      ImmutableMap.Builder<Path, String> assetDirectoriesBuilderForThisModule) {
    File[] dexFiles =
        filesystem
            .getPathForRelativePath(
                pathResolver.getRelativePath(
                    mapOfModuleToSecondaryDexSourcePaths.get(module.getName())))
            .toFile()
            .listFiles();
    if (dexFiles == null) {
      return;
    }
    for (File dexFile : dexFiles) {
      if (dexFile.getName().endsWith(".dex")) {
        dexFileDirectoriesBuilderForThisModule.add(
            filesystem.getPathForRelativePath(dexFile.toPath()));
      } else {
        Path current =
            pathResolver.getRelativePath(
                mapOfModuleToSecondaryDexSourcePaths.get(module.getName()));
        String prefix = current.getParent().getParent().relativize(current).toString();
        assetDirectoriesBuilderForThisModule.put(current, prefix);
      }
    }
  }

  private void addNativeDirectory(
      boolean shouldPackageAssetLibraries,
      APKModule module,
      SourcePathResolver pathResolver,
      ImmutableSet.Builder<Path> nativeLibraryDirectoriesBuilder,
      ImmutableSet.Builder<Path> nativeLibraryDirectoriesBuilderForThisModule) {
    nativeLibraryDirectoriesBuilder.add(
        pathResolver.getRelativePath(nativeFilesInfo.nativeLibsDirs.get().get(module)));
    nativeLibraryDirectoriesBuilderForThisModule.add(
        pathResolver.getRelativePath(nativeFilesInfo.nativeLibsDirs.get().get(module)));
    if (shouldPackageAssetLibraries) {
      return;
    }
    nativeLibraryDirectoriesBuilder.add(
        pathResolver.getRelativePath(nativeFilesInfo.nativeLibsAssetsDirs.get().get(module)));
    nativeLibraryDirectoriesBuilderForThisModule.add(
        pathResolver.getRelativePath(nativeFilesInfo.nativeLibsAssetsDirs.get().get(module)));
  }

  private void addNativeLibraryAsAssetDirectory(
      APKModule module,
      BuildContext context,
      ImmutableSet.Builder<Path> nativeLibraryAsAssetDirectories,
      ImmutableMap.Builder<Path, String> assetDirectoriesBuilderForThisModule,
      ImmutableList.Builder<Step> steps) {
    Preconditions.checkState(
        ExopackageMode.enabledForModules(exopackageModes)
            || !ExopackageMode.enabledForResources(exopackageModes));
    Path pathForNativeLibsAsAssets = getPathForNativeLibsAsAssets();

    Path libSubdirectory =
        pathForNativeLibsAsAssets
            .resolve("assets")
            .resolve(module.isRootModule() ? "lib" : module.getName());

    getStepsForNativeAssets(
        context,
        steps,
        libSubdirectory,
        module.isRootModule() ? "metadata.txt" : "libs.txt",
        module);

    nativeLibraryAsAssetDirectories.add(pathForNativeLibsAsAssets);

    assetDirectoriesBuilderForThisModule.put(pathForNativeLibsAsAssets, "");
  }

  private Path addModuleResourceDirectory(
      APKModule module,
      BuildContext context,
      ImmutableSet.Builder<Path> moduleResourcesDirectories,
      ImmutableList.Builder<Step> steps) {
    SourcePath resourcePath = moduleResourceApkPaths.get(module);

    Path moduleResDirectory =
        BuildTargetPaths.getScratchPath(
            getProjectFilesystem(), buildTarget, "__module_res_" + module.getName() + "_%s__");

    Path unpackDirectory = moduleResDirectory.resolve("assets").resolve(module.getName());

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), unpackDirectory)));
    steps.add(
        new UnzipStep(
            getProjectFilesystem(),
            context.getSourcePathResolver().getAbsolutePath(resourcePath),
            unpackDirectory,
            Optional.empty()));

    moduleResourcesDirectories.add(moduleResDirectory);
    return unpackDirectory;
  }

  private void getStepsForNativeAssets(
      BuildContext context,
      ImmutableList.Builder<Step> steps,
      Path libSubdirectory,
      String metadataFilename,
      APKModule module) {

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), libSubdirectory)));

    // Input asset libraries are sorted in descending filesize order.
    ImmutableSortedSet.Builder<Path> inputAssetLibrariesBuilder =
        ImmutableSortedSet.orderedBy(
            (libPath1, libPath2) -> {
              try {
                ProjectFilesystem filesystem = getProjectFilesystem();
                int filesizeResult =
                    -Long.compare(
                        filesystem.getFileSize(libPath1), filesystem.getFileSize(libPath2));
                int pathnameResult = libPath1.compareTo(libPath2);
                return filesizeResult != 0 ? filesizeResult : pathnameResult;
              } catch (IOException e) {
                return 0;
              }
            });

    if (packageAssetLibraries || !module.isRootModule()) {
      // TODO(cjhopman): This block should probably all be handled by CopyNativeLibraries.
      // TODO(cjhopman): Why is this packaging native libs as assets even when native exopackage is
      // enabled?
      if (nativeFilesInfo.nativeLibsAssetsDirs.isPresent()
          && nativeFilesInfo.nativeLibsAssetsDirs.get().containsKey(module)) {
        // Copy in cxx libraries marked as assets. Filtering and renaming was already done
        // in CopyNativeLibraries.getBuildSteps().
        Path cxxNativeLibsSrc =
            context
                .getSourcePathResolver()
                .getRelativePath(nativeFilesInfo.nativeLibsAssetsDirs.get().get(module));
        steps.add(
            CopyStep.forDirectory(
                getProjectFilesystem(),
                cxxNativeLibsSrc,
                libSubdirectory,
                CopyStep.DirectoryMode.CONTENTS_ONLY));
      }

      // Step that populates a list of libraries and writes a metadata.txt to decompress.
      steps.add(
          createAssetLibrariesMetadataStep(
              libSubdirectory, metadataFilename, module, inputAssetLibrariesBuilder));
    }

    if (compressAssetLibraries || !module.isRootModule()) {
      ImmutableList.Builder<Path> outputAssetLibrariesBuilder = ImmutableList.builder();
      steps.add(
          createRenameAssetLibrariesStep(
              module, inputAssetLibrariesBuilder, outputAssetLibrariesBuilder));
      // Concat and xz compress.
      Path libOutputBlob = libSubdirectory.resolve("libraries.blob");
      steps.add(new ConcatStep(getProjectFilesystem(), outputAssetLibrariesBuilder, libOutputBlob));
      int compressionLevel = xzCompressionLevel.orElse(XzStep.DEFAULT_COMPRESSION_LEVEL);
      steps.add(
          CompressionAlgorithmCreator.createCompressionStep(
              assetCompressionAlgorithm.orElse(CompressionAlgorithm.XZ),
              getProjectFilesystem(),
              libOutputBlob,
              libSubdirectory,
              compressionLevel));
    }
  }

  private AbstractExecutionStep createRenameAssetLibrariesStep(
      APKModule module,
      ImmutableSortedSet.Builder<Path> inputAssetLibrariesBuilder,
      ImmutableList.Builder<Path> outputAssetLibrariesBuilder) {
    return new AbstractExecutionStep("rename_asset_libraries_as_temp_files_" + module.getName()) {
      @Override
      public StepExecutionResult execute(ExecutionContext context) throws IOException {
        ProjectFilesystem filesystem = getProjectFilesystem();
        for (Path libPath : inputAssetLibrariesBuilder.build()) {
          Path tempPath = libPath.resolveSibling(libPath.getFileName() + "~");
          filesystem.move(libPath, tempPath);
          outputAssetLibrariesBuilder.add(tempPath);
        }
        return StepExecutionResults.SUCCESS;
      }
    };
  }

  private AbstractExecutionStep createAssetLibrariesMetadataStep(
      Path libSubdirectory,
      String metadataFilename,
      APKModule module,
      ImmutableSortedSet.Builder<Path> inputAssetLibrariesBuilder) {
    return new AbstractExecutionStep("write_metadata_for_asset_libraries_" + module.getName()) {
      @Override
      public StepExecutionResult execute(ExecutionContext context) throws IOException {
        ProjectFilesystem filesystem = getProjectFilesystem();
        // Walk file tree to find libraries
        filesystem.walkRelativeFileTree(
            libSubdirectory,
            new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                if (!file.toString().endsWith(".so")) {
                  throw new IOException("unexpected file in lib directory");
                }
                inputAssetLibrariesBuilder.add(file);
                return FileVisitResult.CONTINUE;
              }
            });

        // Write a metadata
        ImmutableList.Builder<String> metadataLines = ImmutableList.builder();
        Path metadataOutput = libSubdirectory.resolve(metadataFilename);
        for (Path libPath : inputAssetLibrariesBuilder.build()) {
          // Should return something like x86/libfoo.so
          Path relativeLibPath = libSubdirectory.relativize(libPath);
          long filesize = filesystem.getFileSize(libPath);
          String desiredOutput = relativeLibPath.toString();
          String checksum = filesystem.computeSha256(libPath);
          metadataLines.add(desiredOutput + ' ' + filesize + ' ' + checksum);
        }
        ImmutableList<String> metadata = metadataLines.build();
        if (!metadata.isEmpty()) {
          filesystem.writeLinesToPath(metadata, metadataOutput);
        }
        return StepExecutionResults.SUCCESS;
      }
    };
  }

  private Supplier<KeystoreProperties> getKeystorePropertiesSupplier(
      SourcePathResolver resolver, Path pathToKeystore) {
    return MoreSuppliers.memoize(
        () -> {
          try {
            return KeystoreProperties.createFromPropertiesFile(
                pathToKeystore,
                resolver.getAbsolutePath(keystorePropertiesPath),
                getProjectFilesystem());
          } catch (IOException e) {
            throw new RuntimeException();
          }
        });
  }

  private RepackZipEntriesStep createRepackZipEntriesStep(
      Path signedApkPath, Path compressedApkPath) {
    return new RepackZipEntriesStep(
        getProjectFilesystem(),
        signedApkPath,
        compressedApkPath,
        ImmutableSet.of("resources.arsc"));
  }

  private Iterable<Step> createRedexSteps(
      BuildContext context,
      BuildableContext buildableContext,
      SourcePathResolver resolver,
      Supplier<KeystoreProperties> keystoreProperties,
      Path apkToRedexAndAlign,
      Path redexedApk) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    Path proguardConfigDir = getProguardTextFilesPath();
    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), redexedApk.getParent())));
    ImmutableList<Step> redexSteps =
        ReDexStep.createSteps(
            getProjectFilesystem(),
            androidSdkLocation,
            resolver,
            redexOptions.get(),
            apkToRedexAndAlign,
            redexedApk,
            keystoreProperties,
            proguardConfigDir,
            buildableContext);
    steps.addAll(redexSteps);
    return steps.build();
  }

  private CopyStep createCopyProguardFilesStep(
      SourcePathResolver pathResolver, SourcePath proguardTextFilesPath) {
    return CopyStep.forDirectory(
        getProjectFilesystem(),
        pathResolver.getRelativePath(proguardTextFilesPath),
        getProguardTextFilesPath(),
        CopyStep.DirectoryMode.CONTENTS_ONLY);
  }

  public ProjectFilesystem getProjectFilesystem() {
    return filesystem;
  }

  public BuildTarget getBuildTarget() {
    return buildTarget;
  }

  public Path getManifestPath() {
    return BuildTargetPaths.getGenPath(
        getProjectFilesystem(), getBuildTarget(), "%s/AndroidManifest.xml");
  }

  /** All native-libs-as-assets are copied to this directory before running apkbuilder. */
  private Path getPathForNativeLibsAsAssets() {
    return BuildTargetPaths.getScratchPath(
        getProjectFilesystem(), getBuildTarget(), "__native_libs_as_assets_%s__");
  }

  /** The APK at this path will be jar signed, but not zipaligned. */
  private Path getSignedApkPath() {
    return Paths.get(getUnsignedApkPath().replaceAll("\\.unsigned\\.apk$", ".signed.apk"));
  }

  /** The APK at this path will be zipaligned and jar signed. */
  private Path getZipalignedApkPath() {
    return Paths.get(getUnsignedApkPath().replaceAll("\\.unsigned\\.apk$", ".zipaligned.apk"));
  }

  /** The APK at this path will be zipaligned and v2 signed. */
  Path getFinalApkPath() {
    return Paths.get(getUnsignedApkPath().replaceAll("\\.unsigned\\.apk$", ".apk"));
  }

  /** The APK at this path will have compressed resources, but will not be zipaligned. */
  private Path getCompressedResourcesApkPath() {
    return Paths.get(getUnsignedApkPath().replaceAll("\\.unsigned\\.apk$", ".compressed.apk"));
  }

  private String getUnsignedApkPath() {
    return getPath("%s.unsigned.apk").toString();
  }

  private Path getPath(String format) {
    return BuildTargetPaths.getGenPath(getProjectFilesystem(), getBuildTarget(), format);
  }

  private Path getRedexedApkPath() {
    Path path = BuildTargetPaths.getGenPath(getProjectFilesystem(), getBuildTarget(), "%s__redex");
    return path.resolve(getBuildTarget().getShortName() + ".redex.apk");
  }

  /**
   * Directory of text files used by proguard. Unforunately, this contains both inputs and outputs.
   */
  private Path getProguardTextFilesPath() {
    return BuildTargetPaths.getGenPath(getProjectFilesystem(), getBuildTarget(), "%s/proguard");
  }

  @VisibleForTesting
  static Path getProguardOutputFromInputClasspath(Path proguardConfigDir, Path classpathEntry) {
    // Hehe, this is so ridiculously fragile.
    Preconditions.checkArgument(
        !classpathEntry.isAbsolute(),
        "Classpath entries should be relative rather than absolute paths: %s",
        classpathEntry);
    String obfuscatedName =
        Files.getNameWithoutExtension(classpathEntry.toString()) + "-obfuscated.jar";
    Path dirName = classpathEntry.getParent();
    return proguardConfigDir.resolve(dirName).resolve(obfuscatedName);
  }
}
