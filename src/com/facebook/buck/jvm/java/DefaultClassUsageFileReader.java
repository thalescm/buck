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

package com.facebook.buck.jvm.java;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.ArchiveMemberSourcePath;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.util.ObjectMappers;
import com.facebook.buck.util.exceptions.BuckUncheckedExecutionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/** Provides utility methods for reading dependency file entries. */
class DefaultClassUsageFileReader {
  /** Utility code, not instantiable */
  private DefaultClassUsageFileReader() {}

  private static ImmutableMap<String, ImmutableList<String>> loadClassUsageMap(Path mapFilePath)
      throws IOException {
    return ObjectMappers.readValue(
        mapFilePath, new TypeReference<ImmutableMap<String, ImmutableList<String>>>() {});
  }

  /**
   * This method loads a class usage file that maps JARs to the list of files within those jars that
   * were used. Given our rule's deps, we determine which of these JARS in the class usage file are
   * actually among the deps of our rule.
   */
  public static ImmutableList<SourcePath> loadFromFile(
      ProjectFilesystem projectFilesystem,
      CellPathResolver cellPathResolver,
      Path classUsageFilePath,
      ImmutableMap<Path, SourcePath> jarPathToSourcePath) {
    final ImmutableList.Builder<SourcePath> builder = ImmutableList.builder();
    try {
      final ImmutableSet<Map.Entry<String, ImmutableList<String>>> classUsageEntries =
          loadClassUsageMap(classUsageFilePath).entrySet();
      for (Map.Entry<String, ImmutableList<String>> jarUsedClassesEntry : classUsageEntries) {
        final Path recordedPath = Paths.get(jarUsedClassesEntry.getKey());
        Path jarAbsolutePath =
            recordedPath.isAbsolute()
                ? getAbsolutePathForCellRootedPath(recordedPath, cellPathResolver)
                : projectFilesystem.resolve(recordedPath);
        SourcePath sourcePath = jarPathToSourcePath.get(jarAbsolutePath);
        if (sourcePath == null) {
          // This indicates a dependency that wasn't among the deps of the rule; i.e.,
          // it came from the build environment (JDK, Android SDK, etc.)
          continue;
        }

        for (String classAbsolutePath : jarUsedClassesEntry.getValue()) {
          builder.add(ArchiveMemberSourcePath.of(sourcePath, Paths.get(classAbsolutePath)));
        }
      }
    } catch (IOException e) {
      throw new BuckUncheckedExecutionException(
          e,
          "When loading class usage files from %s.",
          projectFilesystem.resolve(classUsageFilePath));
    }
    return builder.build();
  }

  /**
   * Convert a path rooted in another cell to an absolute path in the filesystem
   *
   * @param cellRootedPath a path beginning with '/cell_name/' followed by a relative path in that
   *     cell
   * @param cellPathResolver the resolver capable of mapping cell_name to absolute root path
   * @return an absolute path: 'path/to/cell/root/' + 'relative/path/in/cell'
   */
  private static Path getAbsolutePathForCellRootedPath(
      Path cellRootedPath, CellPathResolver cellPathResolver) {
    Preconditions.checkArgument(cellRootedPath.isAbsolute(), "Path must begin with /<cell_name>");
    final Iterator<Path> pathIterator = cellRootedPath.iterator();
    final Path cellName = pathIterator.next();
    Path relativeToCellRoot = pathIterator.next();
    while (pathIterator.hasNext()) {
      relativeToCellRoot = relativeToCellRoot.resolve(pathIterator.next());
    }
    return cellPathResolver
        .getCellPath(Optional.of(cellName.toString()))
        .orElseThrow(() -> new AssertionError("Cell name does not exist: " + cellName.toString()))
        .resolve(relativeToCellRoot);
  }
}
