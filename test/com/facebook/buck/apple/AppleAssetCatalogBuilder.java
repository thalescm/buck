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

package com.facebook.buck.apple;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;
import java.util.SortedSet;

public class AppleAssetCatalogBuilder
    extends AbstractNodeBuilder<
        AppleAssetCatalogDescriptionArg.Builder, AppleAssetCatalogDescriptionArg,
        AppleAssetCatalogDescription, AppleAssetCatalog> {

  protected AppleAssetCatalogBuilder(BuildTarget target) {
    super(new AppleAssetCatalogDescription(), target);
  }

  public static AppleAssetCatalogBuilder createBuilder(BuildTarget target) {
    return new AppleAssetCatalogBuilder(target);
  }

  public AppleAssetCatalogBuilder setDirs(SortedSet<SourcePath> dirs) {
    getArgForPopulating().setDirs(dirs);
    return this;
  }

  public AppleAssetCatalogBuilder setLaunchImage(String launchImage) {
    getArgForPopulating().setLaunchImage(launchImage);
    return this;
  }

  public AppleAssetCatalogBuilder setAppIcon(String appIcon) {
    getArgForPopulating().setAppIcon(appIcon);
    return this;
  }
}
