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

package com.facebook.buck.step.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.timing.IncrementingFakeClock;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class TouchStepTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void testGetShortName() {
    Path someFile = Paths.get("a/file.txt");
    TouchStep touchStep = new TouchStep(new FakeProjectFilesystem(tmp.getRoot()), someFile);
    assertEquals("touch", touchStep.getShortName());
  }

  @Test
  public void testFileGetsCreated() throws IOException, InterruptedException {
    Path path = Paths.get("somefile");
    assertFalse(path.toFile().exists());
    ProjectFilesystem projectFilesystem =
        new FakeProjectFilesystem(
            new IncrementingFakeClock(TimeUnit.MILLISECONDS.toNanos(1)),
            tmp.getRoot(),
            ImmutableSet.of());
    TouchStep touchStep = new TouchStep(projectFilesystem, path);
    ExecutionContext executionContext = TestExecutionContext.newInstance();
    touchStep.execute(executionContext);
    assertTrue(projectFilesystem.exists(path));
  }

  @Test
  public void testFileLastModifiedTimeUpdated() throws IOException, InterruptedException {
    Path path = Paths.get("somefile");
    ProjectFilesystem projectFilesystem =
        new FakeProjectFilesystem(
            new IncrementingFakeClock(TimeUnit.MILLISECONDS.toNanos(1)),
            tmp.getRoot(),
            ImmutableSet.of(path));
    FileTime lastModifiedTime = projectFilesystem.getLastModifiedTime(path);
    TouchStep touchStep = new TouchStep(projectFilesystem, path);
    ExecutionContext executionContext = TestExecutionContext.newInstance();
    touchStep.execute(executionContext);
    assertTrue(projectFilesystem.exists(path));
    assertTrue(lastModifiedTime.compareTo(projectFilesystem.getLastModifiedTime(path)) < 0);
  }
}
