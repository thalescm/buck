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

import com.facebook.buck.artifact_cache.config.ArtifactCacheBuckConfig;
import com.facebook.buck.artifact_cache.config.DirCacheEntry;
import com.facebook.buck.event.listener.JavaUtilsLoggingBuildListener;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.util.ExitCode;
import java.io.IOException;
import org.kohsuke.args4j.Option;

public class CleanCommand extends AbstractCommand {

  private static final String KEEP_CACHE_ARG = "--keep-cache";

  @Option(name = KEEP_CACHE_ARG, usage = "Keep the local cache.")
  private boolean keepCache = false;

  private void cleanCell(Cell cell) throws IOException {
    // Ideally, we would like the implementation of this method to be as simple as:
    //
    // getProjectFilesystem().deleteRecursivelyIfExists(BuckConstant.BUCK_OUTPUT_DIRECTORY);
    //
    // However, we want to avoid blowing away directories that IntelliJ indexes, because that tends
    // to make it angry. Currently, those directories are:
    //
    // Project.ANDROID_GEN_DIR
    // BuckConstant.ANNOTATION_DIR
    //
    // However, Buck itself also uses BuckConstant.ANNOTATION_DIR. We need to fix things so that
    // IntelliJ does its default thing to generate code from annotations, and manages/indexes those
    // directories itself so we can blow away BuckConstant.ANNOTATION_DIR as part of `buck clean`.
    // This will also reduce how long `buck project` takes.
    //

    ProjectFilesystem projectFilesystem = cell.getFilesystem();
    // On Windows, you have to close all files that will be deleted.
    // Because buck clean will delete build.log, you must close it first.
    JavaUtilsLoggingBuildListener.closeLogFile();
    projectFilesystem.deleteRecursivelyIfExists(projectFilesystem.getBuckPaths().getScratchDir());
    projectFilesystem.deleteRecursivelyIfExists(projectFilesystem.getBuckPaths().getGenDir());
    projectFilesystem.deleteRecursivelyIfExists(projectFilesystem.getBuckPaths().getTrashDir());

    // Remove dir caches.
    if (!keepCache) {
      ArtifactCacheBuckConfig artifactCacheBuckConfig =
          ArtifactCacheBuckConfig.of(cell.getBuckConfig());
      projectFilesystem.deleteRecursivelyIfExists(projectFilesystem.getBuckPaths().getCacheDir());
      for (DirCacheEntry dirCacheEntry :
          artifactCacheBuckConfig.getCacheEntries().getDirCacheEntries()) {
        projectFilesystem.deleteRecursivelyIfExists(dirCacheEntry.getCacheDir());
      }
    }

    // Clean out any additional directories specified via config setting.
    for (String subPath : cell.getBuckConfig().getCleanAdditionalPaths()) {
      projectFilesystem.deleteRecursivelyIfExists(projectFilesystem.getPath(subPath));
    }
  }

  @Override
  public ExitCode runWithoutHelp(CommandRunnerParams params) throws IOException {
    for (Cell cell : params.getCell().getLoadedCells().values()) {
      cleanCell(cell);
    }
    return ExitCode.SUCCESS;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getShortDescription() {
    return "deletes any generated files";
  }
}
