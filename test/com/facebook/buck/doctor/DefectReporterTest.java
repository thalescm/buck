/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.doctor;

import static org.junit.Assert.assertThat;

import com.facebook.buck.config.FakeBuckConfig;
import com.facebook.buck.doctor.config.DoctorConfig;
import com.facebook.buck.doctor.config.UserLocalConfiguration;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.ObjectMappers;
import com.facebook.buck.util.environment.BuildEnvironmentDescription;
import com.facebook.buck.util.timing.Clock;
import com.facebook.buck.util.timing.DefaultClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

public class DefectReporterTest {

  private static final BuildEnvironmentDescription TEST_ENV_DESCRIPTION =
      BuildEnvironmentDescription.builder()
          .setUser("test_user")
          .setHostname("test_hostname")
          .setOs("test_os")
          .setAvailableCores(1)
          .setSystemMemory(1024L)
          .setBuckDirty(Optional.of(false))
          .setBuckCommit("test_commit")
          .setJavaVersion("test_java_version")
          .setJsonProtocolVersion(1)
          .build();

  private static final UserLocalConfiguration TEST_USER_LOCAL_CONFIGURATION =
      UserLocalConfiguration.of(true, ImmutableMap.of(Paths.get(".buckconfig.local"), "data"));

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Test
  public void testAttachesPaths() throws Exception {
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(temporaryFolder.getRoot());
    DoctorConfig config = DoctorConfig.of(FakeBuckConfig.builder().build());
    Clock clock = new DefaultClock();
    DefectReporter reporter =
        new DefaultDefectReporter(
            filesystem, config, BuckEventBusForTests.newInstance(clock), clock);

    Path fileToBeIncluded = Paths.get("FileToBeIncluded.txt");
    filesystem.touch(fileToBeIncluded);
    String fileToBeIncludedContent = "testcontentbehere";
    filesystem.writeContentsToPath(fileToBeIncludedContent, fileToBeIncluded);

    DefectSubmitResult defectSubmitResult =
        reporter.submitReport(
            DefectReport.builder()
                .setBuildEnvironmentDescription(TEST_ENV_DESCRIPTION)
                .setIncludedPaths(fileToBeIncluded)
                .setUserLocalConfiguration(TEST_USER_LOCAL_CONFIGURATION)
                .build());

    Path reportPath = filesystem.resolve(defectSubmitResult.getReportSubmitLocation().get());
    ZipInspector inspector = new ZipInspector(reportPath);
    inspector.assertFileContents(fileToBeIncluded, fileToBeIncludedContent);
  }

  @Test
  public void testAttachesReport() throws Exception {
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(temporaryFolder.getRoot());
    DoctorConfig config = DoctorConfig.of(FakeBuckConfig.builder().build());
    Clock clock = new DefaultClock();
    DefectReporter reporter =
        new DefaultDefectReporter(
            filesystem, config, BuckEventBusForTests.newInstance(clock), clock);

    DefectSubmitResult defectSubmitResult =
        reporter.submitReport(
            DefectReport.builder()
                .setBuildEnvironmentDescription(TEST_ENV_DESCRIPTION)
                .setUserLocalConfiguration(TEST_USER_LOCAL_CONFIGURATION)
                .build());

    Path reportPath = filesystem.resolve(defectSubmitResult.getReportSubmitLocation().get());
    try (ZipFile zipFile = new ZipFile(reportPath.toFile())) {
      ZipEntry entry = zipFile.getEntry("report.json");
      JsonNode reportNode = ObjectMappers.READER.readTree(zipFile.getInputStream(entry));
      assertThat(
          reportNode.get("buildEnvironmentDescription").get("user").asText(),
          Matchers.equalTo("test_user"));
      assertThat(
          reportNode.get("userLocalConfiguration").get("noBuckCheckPresent").asBoolean(),
          Matchers.equalTo(true));
    }
  }
}
