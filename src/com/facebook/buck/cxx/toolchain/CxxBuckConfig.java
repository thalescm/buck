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

package com.facebook.buck.cxx.toolchain;

import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.cxx.toolchain.linker.DefaultLinkerProvider;
import com.facebook.buck.cxx.toolchain.linker.LinkerProvider;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.model.UserFlavor;
import com.facebook.buck.rules.BinaryBuildRuleToolProvider;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.RuleScheduleInfo;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.ToolProvider;
import com.facebook.buck.rules.tool.config.ToolConfig;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/** Contains platform independent settings for C/C++ rules. */
public class CxxBuckConfig {

  private static final String FLAVORED_CXX_SECTION_PREFIX = "cxx#";
  private static final String UNFLAVORED_CXX_SECTION_PREFIX = "cxx";

  private static final long DEFAULT_MAX_TEST_OUTPUT_SIZE = 8096;

  private final BuckConfig delegate;
  private final String cxxSection;

  public static final String DEFAULT_FLAVOR_LIBRARY_TYPE = "type";
  public static final String DEFAULT_FLAVOR_PLATFORM = "platform";

  /**
   * Constructs set of flavors given in a .buckconfig file, as is specified by section names of the
   * form cxx#{flavor name}.
   */
  public static ImmutableSet<Flavor> getCxxFlavors(BuckConfig config) {
    ImmutableSet.Builder<Flavor> builder = ImmutableSet.builder();
    ImmutableSet<String> sections = config.getSections();
    for (String section : sections) {
      if (section.startsWith(FLAVORED_CXX_SECTION_PREFIX)) {
        builder.add(InternalFlavor.of(section.substring(FLAVORED_CXX_SECTION_PREFIX.length())));
      }
    }
    return builder.build();
  }

  public CxxBuckConfig(BuckConfig delegate) {
    this.delegate = delegate;
    this.cxxSection = UNFLAVORED_CXX_SECTION_PREFIX;
  }

  /*
   * A special constructor for a section of the form cxx#{flavor name}
   * which represents a generated flavor that uses the cxx options defined
   * in that section.
   */
  public CxxBuckConfig(BuckConfig delegate, Flavor flavor) {
    this.delegate = delegate;
    this.cxxSection = FLAVORED_CXX_SECTION_PREFIX + flavor.getName();
  }

  /** @return the environment in which {@link BuckConfig} was created. */
  public ImmutableMap<String, String> getEnvironment() {
    return delegate.getEnvironment();
  }

  /** @return the {@link BuildTarget} which represents the gtest library. */
  public Optional<BuildTarget> getGtestDep() {
    return delegate.getBuildTarget(cxxSection, "gtest_dep");
  }

  /**
   * @return the {@link BuildTarget} which represents the main function that gtest tests should use
   *     by default (if no other main is given).
   */
  public Optional<BuildTarget> getGtestDefaultTestMainDep() {
    return delegate.getBuildTarget(cxxSection, "gtest_default_test_main_dep");
  }

  /** @return the {@link BuildTarget} which represents the boost testing library. */
  public Optional<BuildTarget> getBoostTestDep() {
    return delegate.getBuildTarget(cxxSection, "boost_test_dep");
  }

  public Optional<Path> getPath(String name) {
    return delegate.getPath(cxxSection, name);
  }

  @Nullable
  public PathSourcePath getSourcePath(Path path) {
    return delegate.getPathSourcePath(path);
  }

  public Optional<SourcePath> getSourcePath(String name) {
    return delegate.getSourcePath(cxxSection, name);
  }

  public Optional<String> getDefaultPlatform() {
    return delegate.getValue(cxxSection, "default_platform");
  }

  public Optional<String> getHostPlatform() {
    return delegate.getValue(cxxSection, "host_platform");
  }

  public Optional<ImmutableList<String>> getFlags(String field) {
    Optional<String> value = delegate.getValue(cxxSection, field);
    if (!value.isPresent()) {
      return Optional.empty();
    }
    ImmutableList.Builder<String> split = ImmutableList.builder();
    if (!value.get().trim().isEmpty()) {
      split.addAll(Splitter.on(" ").split(value.get().trim()));
    }
    return Optional.of(split.build());
  }

  /*
   * Constructs the appropriate Archiver for the specified platform.
   */
  public Optional<ArchiverProvider> getArchiverProvider(Platform defaultPlatform) {
    Optional<ToolProvider> toolProvider =
        delegate.getView(ToolConfig.class).getToolProvider(cxxSection, "ar");
    return toolProvider.map(
        archiver -> {
          Optional<Platform> archiverPlatform =
              delegate.getEnum(cxxSection, "archiver_platform", Platform.class);
          return ArchiverProvider.from(archiver, archiverPlatform.orElse(defaultPlatform));
        });
  }

  /** @return the maximum size in bytes of test output to report in test results. */
  public long getMaximumTestOutputSize() {
    return delegate
        .getLong(cxxSection, "max_test_output_size")
        .orElse(DEFAULT_MAX_TEST_OUTPUT_SIZE);
  }

  private Optional<CxxToolProviderParams> getCxxToolProviderParams(
      String field, Optional<CxxToolProvider.Type> defaultType) {
    Optional<String> value = delegate.getValue(cxxSection, field);
    if (!value.isPresent()) {
      return Optional.empty();
    }
    String source = String.format("[%s] %s", cxxSection, field);
    Optional<BuildTarget> target = delegate.getMaybeBuildTarget(cxxSection, field);
    Optional<CxxToolProvider.Type> type =
        delegate
            .getEnum(cxxSection, field + "_type", CxxToolProvider.Type.class)
            .map(Optional::of)
            .orElse(defaultType);
    if (type.isPresent() && type.get() == CxxToolProvider.Type.DEFAULT) {
      type = Optional.of(CxxToolProvider.Type.GCC);
    }
    if (target.isPresent()) {
      return Optional.of(
          CxxToolProviderParams.builder()
              .setSource(source)
              .setBuildTarget(target.get())
              .setType(type.orElse(CxxToolProvider.Type.GCC))
              .build());
    } else {
      return Optional.of(
          CxxToolProviderParams.builder()
              .setSource(source)
              .setPath(delegate.getPathSourcePath(delegate.getRequiredPath(cxxSection, field)))
              .setType(type)
              .build());
    }
  }

  public Optional<PreprocessorProvider> getPreprocessorProvider(String field) {
    Optional<CxxToolProvider.Type> defaultType = Optional.empty();
    Optional<CxxToolProviderParams> params = getCxxToolProviderParams(field, defaultType);
    if (!params.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(params.get().getPreprocessorProvider());
  }

  public Optional<CompilerProvider> getCompilerProvider(String field) {
    Optional<CxxToolProviderParams> params = getCxxToolProviderParams(field, Optional.empty());
    if (!params.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(params.get().getCompilerProvider());
  }

  public Optional<LinkerProvider> getLinkerProvider(String field, LinkerProvider.Type defaultType) {
    Optional<ToolProvider> toolProvider =
        delegate.getView(ToolConfig.class).getToolProvider(cxxSection, field);
    if (!toolProvider.isPresent()) {
      return Optional.empty();
    }
    Optional<LinkerProvider.Type> type =
        delegate.getEnum(cxxSection, "linker_platform", LinkerProvider.Type.class);
    return Optional.of(new DefaultLinkerProvider(type.orElse(defaultType), toolProvider.get()));
  }

  public HeaderVerification getHeaderVerification() {
    return HeaderVerification.builder()
        .setMode(
            delegate
                .getEnum(cxxSection, "untracked_headers", HeaderVerification.Mode.class)
                .orElse(HeaderVerification.Mode.IGNORE))
        .addAllWhitelist(delegate.getListWithoutComments(cxxSection, "untracked_headers_whitelist"))
        .build();
  }

  public boolean getPublicHeadersSymlinksEnabled() {
    return delegate.getBooleanValue(cxxSection, "exported_headers_symlinks_enabled", true);
  }

  public boolean getPrivateHeadersSymlinksEnabled() {
    return delegate.getBooleanValue(cxxSection, "headers_symlinks_enabled", true);
  }

  public Optional<RuleScheduleInfo> getLinkScheduleInfo() {
    Optional<Long> linkWeight = delegate.getLong(cxxSection, "link_weight");
    if (!linkWeight.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        RuleScheduleInfo.builder().setJobsMultiplier(linkWeight.get().intValue()).build());
  }

  public boolean shouldCacheLinks() {
    return delegate.getBooleanValue(cxxSection, "cache_links", true);
  }

  public boolean isPCHEnabled() {
    return delegate.getBooleanValue(cxxSection, "pch_enabled", true);
  }

  public PchUnavailableMode getPchUnavailableMode() {
    return delegate
        .getEnum(cxxSection, "pch_unavailable", PchUnavailableMode.class)
        .orElse(PchUnavailableMode.ERROR);
  }

  public boolean sandboxSources() {
    return delegate.getBooleanValue(cxxSection, "sandbox_sources", false);
  }

  public ArchiveContents getArchiveContents() {
    return delegate
        .getEnum(cxxSection, "archive_contents", ArchiveContents.class)
        .orElse(ArchiveContents.NORMAL);
  }

  public ImmutableMap<String, Flavor> getDefaultFlavorsForRuleType(BuildRuleType type) {
    return ImmutableMap.copyOf(
        Maps.transformValues(
            delegate.getEntriesForSection("defaults." + type.getName()), InternalFlavor::of));
  }

  public int getDebugPathSanitizerLimit() {
    return delegate.getInteger(cxxSection, "debug_path_sanitizer_limit").orElse(250);
  }

  /** @return whether to remap to the underlying host platform or to use #default */
  public boolean getShouldRemapHostPlatform() {
    return delegate.getBooleanValue(cxxSection, "should_remap_host_platform", false);
  }

  public Optional<ToolProvider> getToolProvider(String name) {
    return delegate.getView(ToolConfig.class).getToolProvider(cxxSection, name);
  }

  public boolean isUniqueLibraryNameEnabled() {
    return delegate.getBooleanValue(cxxSection, "unique_library_name_enabled", false);
  }

  public boolean getDefaultReexportAllHeaderDependencies() {
    return delegate.getBooleanValue(cxxSection, "default_reexport_all_header_dependencies", true);
  }

  /** @return whether to enable shared library interfaces. */
  public SharedLibraryInterfaceParams.Type getSharedLibraryInterfaces() {

    // Check for an explicit setting.
    Optional<SharedLibraryInterfaceParams.Type> setting =
        delegate.getEnum(cxxSection, "shlib_interfaces", SharedLibraryInterfaceParams.Type.class);
    if (setting.isPresent()) {
      return setting.get();
    }

    // For backwards compatibility, check the older boolean setting.
    Optional<Boolean> oldSetting = delegate.getBoolean(cxxSection, "shared_library_interfaces");
    if (oldSetting.isPresent()) {
      return oldSetting.get()
          ? SharedLibraryInterfaceParams.Type.ENABLED
          : SharedLibraryInterfaceParams.Type.DISABLED;
    }

    // Default.
    return SharedLibraryInterfaceParams.Type.DISABLED;
  }

  public boolean isDeprecatedPrebuiltCxxLibraryApiEnabled() {
    return delegate.getBooleanValue(
        cxxSection, "enable_deprecated_prebuilt_cxx_library_api", false);
  }

  /** @return the list of flavors that buck will consider valid when building the target graph. */
  public ImmutableSet<Flavor> getDeclaredPlatforms() {
    return delegate
        .getListWithoutComments(cxxSection, "declared_platforms")
        .stream()
        .map(s -> UserFlavor.of(s, String.format("Declared platform: %s", s)))
        .collect(ImmutableSet.toImmutableSet());
  }

  public BuckConfig getDelegate() {
    return delegate;
  }

  @Value.Immutable
  @BuckStyleImmutable
  abstract static class AbstractCxxToolProviderParams {

    public abstract String getSource();

    public abstract Optional<BuildTarget> getBuildTarget();

    public abstract Optional<PathSourcePath> getPath();

    public abstract Optional<CxxToolProvider.Type> getType();

    @Value.Check
    protected void check() {
      Preconditions.checkState(getBuildTarget().isPresent() || getPath().isPresent());
      Preconditions.checkState(!getBuildTarget().isPresent() || getType().isPresent());
    }

    public PreprocessorProvider getPreprocessorProvider() {
      if (getBuildTarget().isPresent()) {
        return new PreprocessorProvider(
            new BinaryBuildRuleToolProvider(getBuildTarget().get(), getSource()), getType().get());
      } else {
        return new PreprocessorProvider(getPath().get(), getType());
      }
    }

    public CompilerProvider getCompilerProvider() {
      if (getBuildTarget().isPresent()) {
        return new CompilerProvider(
            new BinaryBuildRuleToolProvider(getBuildTarget().get(), getSource()), getType().get());
      } else {
        return new CompilerProvider(getPath().get(), getType());
      }
    }
  }
}
