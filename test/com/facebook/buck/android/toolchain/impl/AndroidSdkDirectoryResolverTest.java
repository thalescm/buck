/*
 * Copyright 2013-present Facebook, Inc.
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
package com.facebook.buck.android.toolchain.impl;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.android.FakeAndroidBuckConfig;
import com.facebook.buck.android.toolchain.ndk.impl.AndroidNdkHelper;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AndroidSdkDirectoryResolverTest {

  @Rule public TemporaryPaths tmpDir = new TemporaryPaths();

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void throwAtAbsentSdk() {
    AndroidSdkDirectoryResolver resolver =
        new AndroidSdkDirectoryResolver(
            tmpDir.getRoot().getFileSystem(), ImmutableMap.of(), AndroidNdkHelper.DEFAULT_CONFIG);

    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(AndroidSdkDirectoryResolver.SDK_NOT_FOUND_MESSAGE);
    resolver.getSdkOrThrow();
  }

  @Test
  public void throwAtSdkPathIsNotDirectory() throws IOException {
    Path file = tmpDir.getRoot().resolve(tmpDir.newFile("file"));
    AndroidSdkDirectoryResolver resolver =
        new AndroidSdkDirectoryResolver(
            tmpDir.getRoot().getFileSystem(),
            ImmutableMap.of("ANDROID_SDK", file.toString()),
            AndroidNdkHelper.DEFAULT_CONFIG);

    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        String.format(
            AndroidSdkDirectoryResolver.INVALID_DIRECTORY_MESSAGE_TEMPLATE, "ANDROID_SDK", file));
    resolver.getSdkOrThrow();
  }

  @Test
  public void testGetSdkFromBuckconfig() throws IOException {
    Path sdkDir = tmpDir.newFolder("sdk");
    AndroidSdkDirectoryResolver resolver =
        new AndroidSdkDirectoryResolver(
            tmpDir.getRoot().getFileSystem(),
            ImmutableMap.of(),
            FakeAndroidBuckConfig.builder().setSdkPath(sdkDir.toString()).build());

    assertEquals(sdkDir, resolver.getSdkOrThrow());
  }

  @Test
  public void testSdkFromEnvironmentSupercedesBuckconfig() throws IOException {
    Path sdkDir = tmpDir.newFolder("sdk");
    AndroidSdkDirectoryResolver resolver =
        new AndroidSdkDirectoryResolver(
            tmpDir.getRoot().getFileSystem(),
            ImmutableMap.of("ANDROID_SDK", sdkDir.resolve("also-wrong").toString()),
            FakeAndroidBuckConfig.builder().setSdkPath(sdkDir.toString()).build());
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        String.format(
            AndroidSdkDirectoryResolver.INVALID_DIRECTORY_MESSAGE_TEMPLATE,
            "ANDROID_SDK",
            sdkDir.resolve("also-wrong")));
    resolver.getSdkOrThrow();
  }
}
