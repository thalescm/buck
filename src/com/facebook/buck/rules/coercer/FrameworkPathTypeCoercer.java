/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.SourceTreePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FrameworkPathTypeCoercer implements TypeCoercer<FrameworkPath> {

  private final TypeCoercer<SourcePath> sourcePathTypeCoercer;

  public FrameworkPathTypeCoercer(TypeCoercer<SourcePath> sourcePathTypeCoercer) {
    this.sourcePathTypeCoercer = sourcePathTypeCoercer;
  }

  @Override
  public Class<FrameworkPath> getOutputClass() {
    return FrameworkPath.class;
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    for (Class<?> type : types) {
      if (type.isAssignableFrom(SourceTreePath.class)) {
        return true;
      }
    }
    return sourcePathTypeCoercer.hasElementClass(types);
  }

  @Override
  public void traverse(CellPathResolver cellRoots, FrameworkPath object, Traversal traversal) {
    switch (object.getType()) {
      case SOURCE_TREE_PATH:
        traversal.traverse(object.getSourceTreePath().get());
        break;
      case SOURCE_PATH:
        sourcePathTypeCoercer.traverse(cellRoots, object.getSourcePath().get(), traversal);
        break;
      default:
        throw new RuntimeException("Unhandled type: " + object.getType());
    }
  }

  @Override
  public FrameworkPath coerce(
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      Path pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (object instanceof String) {
      Path path = Paths.get((String) object);

      String firstElement =
          Preconditions.checkNotNull(Iterables.getFirst(path, Paths.get(""))).toString();

      if (firstElement.startsWith("$")) { // NOPMD - length() > 0 && charAt(0) == '$' is ridiculous
        Optional<PBXReference.SourceTree> sourceTree =
            PBXReference.SourceTree.fromBuildSetting(firstElement);
        if (sourceTree.isPresent()) {
          int nameCount = path.getNameCount();
          if (nameCount < 2) {
            throw new HumanReadableException(
                "Invalid source tree path: '%s'. Should have at least one path component after"
                    + "'%s'.",
                path, firstElement);
          }
          return FrameworkPath.ofSourceTreePath(
              new SourceTreePath(
                  sourceTree.get(), path.subpath(1, path.getNameCount()), Optional.empty()));
        } else {
          throw new HumanReadableException(
              "Unknown SourceTree: '%s'. Should be one of: %s",
              firstElement,
              Joiner.on(", ")
                  .join(
                      Iterables.transform(
                          ImmutableList.copyOf(PBXReference.SourceTree.values()),
                          input -> "$" + input.toString())));
        }
      } else {
        return FrameworkPath.ofSourcePath(
            sourcePathTypeCoercer.coerce(cellRoots, filesystem, pathRelativeToProjectRoot, object));
      }
    }

    throw CoerceFailedException.simple(
        object, getOutputClass(), "input should be either a source tree path or a source path");
  }
}
