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

package com.facebook.buck.zip;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.util.zip.CustomZipEntry;
import com.facebook.buck.util.zip.CustomZipOutputStream;
import com.facebook.buck.util.zip.ZipCompressionLevel;
import com.facebook.buck.util.zip.ZipOutputStreams;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A command that creates a copy of a ZIP archive, making sure that certain user-specified entries
 * are packed with a certain compression level.
 *
 * <p>Can be used, for instance, to force the resources.arsc file in an Android .apk to be
 * compressed.
 */
public class RepackZipEntriesStep implements Step {

  private final ProjectFilesystem filesystem;
  private final Path inputPath;
  private final Path outputPath;
  private final ImmutableSet<String> entries;
  private final ZipCompressionLevel compressionLevel;

  /**
   * Creates a {@link RepackZipEntriesStep}. A temporary directory will be created and used to
   * extract entries. Entries will be packed with the maximum compression level.
   *
   * @param inputPath input archive
   * @param outputPath destination archive
   * @param entries files to repack (e.g. {@code ImmutableSet.of("resources.arsc")})
   */
  public RepackZipEntriesStep(
      ProjectFilesystem filesystem, Path inputPath, Path outputPath, ImmutableSet<String> entries) {
    this(filesystem, inputPath, outputPath, entries, ZipCompressionLevel.MAX);
  }

  /**
   * Creates a {@link RepackZipEntriesStep}.
   *
   * @param inputPath input archive
   * @param outputPath destination archive
   * @param entries files to repack (e.g. {@code ImmutableSet.of("resources.arsc")})
   * @param compressionLevel the level of compression to use
   */
  public RepackZipEntriesStep(
      ProjectFilesystem filesystem,
      Path inputPath,
      Path outputPath,
      ImmutableSet<String> entries,
      ZipCompressionLevel compressionLevel) {
    this.filesystem = filesystem;
    this.inputPath = inputPath;
    this.outputPath = outputPath;
    this.entries = entries;
    this.compressionLevel = compressionLevel;
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context)
      throws IOException, InterruptedException {
    Path inputFile = filesystem.getPathForRelativePath(inputPath);
    Path outputFile = filesystem.getPathForRelativePath(outputPath);
    try (ZipInputStream in =
            new ZipInputStream(new BufferedInputStream(Files.newInputStream(inputFile)));
        CustomZipOutputStream out = ZipOutputStreams.newOutputStream(outputFile)) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        CustomZipEntry customEntry = new CustomZipEntry(entry);
        if (entries.contains(customEntry.getName())) {
          customEntry.setCompressionLevel(compressionLevel.getValue());
        }

        InputStream toUse;
        // If we're using STORED files, we must pre-calculate the CRC.
        if (customEntry.getMethod() == ZipEntry.STORED) {
          try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ByteStreams.copy(in, bos);
            byte[] bytes = bos.toByteArray();
            customEntry.setCrc(Hashing.crc32().hashBytes(bytes).padToLong());
            customEntry.setSize(bytes.length);
            customEntry.setCompressedSize(bytes.length);
            toUse = new ByteArrayInputStream(bytes);
          }
        } else {
          toUse = in;
        }

        out.putNextEntry(customEntry);
        ByteStreams.copy(toUse, out);
        out.closeEntry();
      }

      return StepExecutionResults.SUCCESS;
    }
  }

  @Override
  public String getShortName() {
    return "repack zip";
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return String.format("repack %s in %s", inputPath, outputPath);
  }
}
