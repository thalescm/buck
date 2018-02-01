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

package com.facebook.buck.testutil.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.dd.plist.BinaryPropertyListParser;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.facebook.buck.cli.Main;
import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.WatchmanFactory;
import com.facebook.buck.io.WatchmanWatcher;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.filesystem.BuckPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.io.filesystem.impl.DefaultProjectFilesystemFactory;
import com.facebook.buck.jvm.java.JavaCompilationConstants;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.CellConfig;
import com.facebook.buck.rules.CellProviderFactory;
import com.facebook.buck.rules.DefaultCellPathResolver;
import com.facebook.buck.testutil.AbstractWorkspace;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.CapturingPrintStream;
import com.facebook.buck.util.CommandLineException;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.MoreStrings;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.Threads;
import com.facebook.buck.util.config.Config;
import com.facebook.buck.util.config.Configs;
import com.facebook.buck.util.environment.Architecture;
import com.facebook.buck.util.environment.CommandMode;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.trace.ChromeTraceParser;
import com.facebook.buck.util.trace.ChromeTraceParser.ChromeTraceEventMatcher;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.martiansoftware.nailgun.NGContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.tools.ToolProvider;
import org.hamcrest.Matchers;

/**
 * {@link ProjectWorkspace} is a directory that contains a Buck project, complete with build files.
 *
 * <p>When {@link #setUp()} is invoked, the project files are cloned from a directory of testdata
 * into a tmp directory according to the following rule:
 *
 * <ul>
 *   <li>Files with the {@code .fixture} extension will be copied and renamed without the extension.
 *   <li>Files with the {@code .expected} extension will not be copied.
 * </ul>
 *
 * After {@link #setUp()} is invoked, the test should invoke Buck in that directory. As this is an
 * integration test, we expect that files will be written as a result of invoking Buck.
 *
 * <p>After Buck has been run, invoke {@link #verify()} to verify that Buck wrote the correct files.
 * For each file in the testdata directory with the {@code .expected} extension, {@link #verify()}
 * will check that a file with the same relative path (but without the {@code .expected} extension)
 * exists in the tmp directory. If not, {@link org.junit.Assert#fail()} will be invoked.
 */
public class ProjectWorkspace extends AbstractWorkspace {

  private static final String PATH_TO_BUILD_LOG = "buck-out/bin/build.log";

  public static final String TEST_CELL_LOCATION =
      "test/com/facebook/buck/testutil/integration/testlibs";

  private boolean isSetUp = false;
  private final Path templatePath;
  private final boolean addBuckRepoCell;
  private final ProcessExecutor processExecutor;
  @Nullable private ProjectFilesystemAndConfig projectFilesystemAndConfig;
  @Nullable private Main.KnownBuildRuleTypesFactoryFactory knownBuildRuleTypesFactoryFactory;

  private static class ProjectFilesystemAndConfig {

    private final ProjectFilesystem projectFilesystem;
    private final Config config;

    private ProjectFilesystemAndConfig(ProjectFilesystem projectFilesystem, Config config) {
      this.projectFilesystem = projectFilesystem;
      this.config = config;
    }
  }

  @VisibleForTesting
  ProjectWorkspace(Path templateDir, Path targetFolder, boolean addBuckRepoCell) {
    super(targetFolder);
    this.templatePath = templateDir;
    this.addBuckRepoCell = addBuckRepoCell;
    this.processExecutor = new DefaultProcessExecutor(new TestConsole());
  }

  @VisibleForTesting
  ProjectWorkspace(Path templateDir, final Path targetFolder) {
    this(templateDir, targetFolder, false);
  }

  private ProjectFilesystemAndConfig getProjectFilesystemAndConfig()
      throws InterruptedException, IOException {
    if (projectFilesystemAndConfig == null) {
      Config config = Configs.createDefaultConfig(destPath);
      projectFilesystemAndConfig =
          new ProjectFilesystemAndConfig(
              TestProjectFilesystems.createProjectFilesystem(destPath, config), config);
    }
    return projectFilesystemAndConfig;
  }

  public ProjectWorkspace setUp() throws IOException {
    addTemplateToWorkspace(templatePath);

    if (addBuckRepoCell) {
      addBuckConfigLocalOption(
          "repositories", "buck", Paths.get(TEST_CELL_LOCATION).toAbsolutePath().toString());
    }

    // Enable the JUL build log.  This log is very verbose but rarely useful,
    // so it's disabled by default.
    addBuckConfigLocalOption("log", "jul_build_log", "true");

    isSetUp = true;
    return this;
  }

  public BuckPaths getBuckPaths() throws InterruptedException, IOException {
    return getProjectFilesystemAndConfig().projectFilesystem.getBuckPaths();
  }

  public ProcessResult runBuckBuild(String... args) throws IOException {
    String[] totalArgs = new String[args.length + 1];
    totalArgs[0] = "build";
    System.arraycopy(args, 0, totalArgs, 1, args.length);
    return runBuckCommand(totalArgs);
  }

  public ProcessResult runBuckTest(String... args) throws IOException {
    String[] totalArgs = new String[args.length + 1];
    totalArgs[0] = "test";
    System.arraycopy(args, 0, totalArgs, 1, args.length);
    return runBuckCommand(totalArgs);
  }

  public ProcessResult runBuckDistBuildRun(String... args) throws IOException {
    String[] totalArgs = new String[args.length + 2];
    totalArgs[0] = "distbuild";
    totalArgs[1] = "run";
    System.arraycopy(args, 0, totalArgs, 2, args.length);
    return runBuckCommand(totalArgs);
  }

  private ImmutableMap<String, String> buildMultipleAndReturnStringOutputs(String... args)
      throws IOException {
    // Add in `--show-output` to the build, so we can parse the output paths after the fact.
    ImmutableList<String> buildArgs =
        ImmutableList.<String>builder().add("--show-output").add(args).build();
    ProcessResult buildResult = runBuckBuild(buildArgs.toArray(new String[buildArgs.size()]));
    buildResult.assertSuccess();

    // Grab the stdout lines, which have the build outputs.
    List<String> lines =
        Splitter.on(CharMatcher.anyOf(System.lineSeparator()))
            .trimResults()
            .omitEmptyStrings()
            .splitToList(buildResult.getStdout());

    Splitter lineSplitter = Splitter.on(' ').trimResults();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (String line : lines) {
      List<String> fields = lineSplitter.splitToList(line);
      assertThat(fields, Matchers.hasSize(2));
      builder.put(fields.get(0), fields.get(1));
    }

    return builder.build();
  }

  public ImmutableMap<String, Path> buildMultipleAndReturnOutputs(String... args)
      throws IOException {
    return buildMultipleAndReturnStringOutputs(args)
        .entrySet()
        .stream()
        .collect(
            ImmutableMap.toImmutableMap(
                entry -> entry.getKey(), entry -> getPath(entry.getValue())));
  }

  public Path buildAndReturnOutput(String... args) throws IOException {
    ImmutableMap<String, Path> outputs = buildMultipleAndReturnOutputs(args);

    // Verify we only have a single output.
    assertThat(
        String.format(
            "expected only a single build target in command `%s`: %s",
            ImmutableList.copyOf(args), outputs),
        outputs.entrySet(),
        Matchers.hasSize(1));

    return outputs.values().iterator().next();
  }

  public ImmutableMap<String, Path> buildMultipleAndReturnRelativeOutputs(String... args)
      throws IOException {
    return buildMultipleAndReturnStringOutputs(args)
        .entrySet()
        .stream()
        .collect(
            ImmutableMap.toImmutableMap(
                entry -> entry.getKey(), entry -> Paths.get(entry.getValue())));
  }

  public Path buildAndReturnRelativeOutput(String... args) throws IOException {
    ImmutableMap<String, Path> outputs = buildMultipleAndReturnRelativeOutputs(args);

    // Verify we only have a single output.
    assertThat(
        String.format(
            "expected only a single build target in command `%s`: %s",
            ImmutableList.copyOf(args), outputs),
        outputs.entrySet(),
        Matchers.hasSize(1));

    return outputs.values().iterator().next();
  }

  public ProcessExecutor.Result runJar(Path jar, ImmutableList<String> vmArgs, String... args)
      throws IOException, InterruptedException {
    List<String> command =
        ImmutableList.<String>builder()
            .addAll(JavaCompilationConstants.DEFAULT_JAVA_COMMAND_PREFIX)
            .addAll(vmArgs)
            .add("-jar")
            .add(jar.toString())
            .addAll(ImmutableList.copyOf(args))
            .build();
    return doRunCommand(command);
  }

  public ProcessExecutor.Result runJar(Path jar, String... args)
      throws IOException, InterruptedException {
    return runJar(jar, ImmutableList.of(), args);
  }

  public ProcessExecutor.Result runCommand(String exe, String... args)
      throws IOException, InterruptedException {
    List<String> command =
        ImmutableList.<String>builder().add(exe).addAll(ImmutableList.copyOf(args)).build();
    return doRunCommand(command);
  }

  private ProcessExecutor.Result doRunCommand(List<String> command)
      throws IOException, InterruptedException {
    ProcessExecutorParams params =
        ProcessExecutorParams.builder()
            .setCommand(command)
            .setDirectory(destPath.toAbsolutePath())
            .build();
    return processExecutor.launchAndExecute(params);
  }

  /**
   * Runs Buck with the specified list of command-line arguments.
   *
   * @param args to pass to {@code buck}, so that could be {@code ["build", "//path/to:target"]},
   *     {@code ["project"]}, etc.
   * @return the result of running Buck, which includes the exit code, stdout, and stderr.
   */
  public ProcessResult runBuckCommand(String... args) throws IOException {
    return runBuckCommandWithEnvironmentOverridesAndContext(
        destPath, Optional.empty(), ImmutableMap.of(), args);
  }

  public ProcessResult runBuckCommand(ImmutableMap<String, String> environment, String... args)
      throws IOException {
    return runBuckCommandWithEnvironmentOverridesAndContext(
        destPath, Optional.empty(), environment, args);
  }

  public ProcessResult runBuckCommand(Path repoRoot, String... args) throws IOException {
    return runBuckCommandWithEnvironmentOverridesAndContext(
        repoRoot, Optional.empty(), ImmutableMap.of(), args);
  }

  public ProcessResult runBuckdCommand(String... args) throws IOException {
    try (TestContext context = new TestContext()) {
      return runBuckdCommand(context, args);
    }
  }

  public ProcessResult runBuckdCommand(ImmutableMap<String, String> environment, String... args)
      throws IOException {
    try (TestContext context = new TestContext(environment)) {
      return runBuckdCommand(context, args);
    }
  }

  public ProcessResult runBuckdCommand(NGContext context, String... args) throws IOException {
    return runBuckdCommand(context, new CapturingPrintStream(), args);
  }

  public ProcessResult runBuckdCommand(
      NGContext context, CapturingPrintStream stderr, String... args) throws IOException {
    assumeTrue(
        "watchman must exist to run buckd",
        new ExecutableFinder(Platform.detect())
            .getOptionalExecutable(Paths.get("watchman"), ImmutableMap.copyOf(System.getenv()))
            .isPresent());
    return runBuckCommandWithEnvironmentOverridesAndContext(
        destPath, Optional.of(context), ImmutableMap.of(), stderr, args);
  }

  public ProcessResult runBuckCommandWithEnvironmentOverridesAndContext(
      Path repoRoot,
      Optional<NGContext> context,
      ImmutableMap<String, String> environmentOverrides,
      String... args)
      throws IOException {
    return runBuckCommandWithEnvironmentOverridesAndContext(
        repoRoot, context, environmentOverrides, new CapturingPrintStream(), args);
  }

  public ProcessResult runBuckCommandWithEnvironmentOverridesAndContext(
      Path repoRoot,
      Optional<NGContext> context,
      ImmutableMap<String, String> environmentOverrides,
      CapturingPrintStream stderr,
      String... args)
      throws IOException {
    try {
      assertTrue("setUp() must be run before this method is invoked", isSetUp);
      CapturingPrintStream stdout = new CapturingPrintStream();
      InputStream stdin = new ByteArrayInputStream("".getBytes());

      // Construct a limited view of the parent environment for the child.
      // TODO(#5754812): we should eventually get tests working without requiring these be set.
      ImmutableList<String> inheritedEnvVars =
          ImmutableList.of(
              "ANDROID_HOME",
              "ANDROID_NDK",
              "ANDROID_NDK_REPOSITORY",
              "ANDROID_SDK",
              // TODO(grumpyjames) Write an equivalent of the groovyc and startGroovy
              // scripts provided by the groovy distribution in order to remove these two.
              "GROOVY_HOME",
              "JAVA_HOME",
              "NDK_HOME",
              "PATH",
              "PATHEXT",

              // Needed by ndk-build on Windows
              "OS",
              "ProgramW6432",
              "ProgramFiles(x86)",

              // The haskell integration tests call into GHC, which needs HOME to be set.
              "HOME",

              // TODO(#6586154): set TMP variable for ShellSteps
              "TMP");
      Map<String, String> envBuilder = new HashMap<>();
      for (String variable : inheritedEnvVars) {
        String value = System.getenv(variable);
        if (value != null) {
          envBuilder.put(variable, value);
        }
      }
      envBuilder.putAll(environmentOverrides);
      ImmutableMap<String, String> sanizitedEnv = ImmutableMap.copyOf(envBuilder);

      Main main =
          knownBuildRuleTypesFactoryFactory == null
              ? new Main(stdout, stderr, stdin, context)
              : new Main(stdout, stderr, stdin, knownBuildRuleTypesFactoryFactory, context);
      ExitCode exitCode;
      try {
        exitCode =
            main.runMainWithExitCode(
                new BuildId(),
                repoRoot,
                sanizitedEnv,
                CommandMode.TEST,
                WatchmanWatcher.FreshInstanceAction.NONE,
                System.nanoTime(),
                ImmutableList.copyOf(args));
      } catch (InterruptedException e) {
        e.printStackTrace(stderr);
        exitCode = ExitCode.BUILD_ERROR;
        Threads.interruptCurrentThread();
      } catch (CommandLineException e) {
        stderr.println(e.getMessage());
        exitCode = ExitCode.COMMANDLINE_ERROR;
      } catch (BuildFileParseException e) {
        stderr.println(e.getHumanReadableErrorMessage());
        exitCode = ExitCode.PARSE_ERROR;
      }

      return new ProcessResult(
          exitCode,
          stdout.getContentsAsString(Charsets.UTF_8),
          stderr.getContentsAsString(Charsets.UTF_8));
    } finally {
      // javac has a global cache of zip/jar file content listings. It determines the validity of
      // a given cache entry based on the modification time of the zip file in question. In normal
      // usage, this is fine. However, in tests, we often will do a build, change something, and
      // then rapidly do another build. If this happens quickly, javac can be operating from
      // incorrect information when reading a jar file, resulting in "bad class file" or
      // "corrupted zip file" errors. We work around this for testing purposes by reaching inside
      // the compiler and clearing the cache.
      try {
        Class<?> cacheClass =
            Class.forName(
                "com.sun.tools.javac.file.ZipFileIndexCache",
                false,
                ToolProvider.getSystemToolClassLoader());

        Method getSharedInstanceMethod = cacheClass.getMethod("getSharedInstance");
        Method clearCacheMethod = cacheClass.getMethod("clearCache");

        Object cache = getSharedInstanceMethod.invoke(cacheClass);
        clearCacheMethod.invoke(cache);
      } catch (ClassNotFoundException
          | IllegalAccessException
          | InvocationTargetException
          | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Runs an event-driven parser on {@code buck-out/log/build.trace}, which is a symlink to the
   * trace of the most recent invocation of Buck (which may not have been a {@code buck build}).
   *
   * @see ChromeTraceParser#parse(Path, Set)
   */
  public Map<ChromeTraceEventMatcher<?>, Object> parseTraceFromMostRecentBuckInvocation(
      Set<ChromeTraceEventMatcher<?>> matchers) throws InterruptedException, IOException {
    ProjectFilesystem projectFilesystem = getProjectFilesystemAndConfig().projectFilesystem;
    ChromeTraceParser parser = new ChromeTraceParser(projectFilesystem);
    return parser.parse(
        projectFilesystem.getBuckPaths().getLogDir().resolve("build.trace"), matchers);
  }

  public void enableDirCache() throws IOException {
    addBuckConfigLocalOption("cache", "mode", "dir");
  }

  public void setupCxxSandboxing(boolean sandboxSources) throws IOException {
    addBuckConfigLocalOption("cxx", "sandbox_sources", Boolean.toString(sandboxSources));
  }

  public void disableThreadLimitOverride() throws IOException {
    removeBuckConfigLocalOption("build", "threads");
  }

  public void setKnownBuildRuleTypesFactoryFactory(
      @Nullable Main.KnownBuildRuleTypesFactoryFactory knownBuildRuleTypesFactoryFactory) {
    this.knownBuildRuleTypesFactoryFactory = knownBuildRuleTypesFactoryFactory;
  }

  public void resetBuildLogFile() throws IOException {
    writeContentsToPath("", PATH_TO_BUILD_LOG);
  }

  public BuckBuildLog getBuildLog() throws IOException {
    return BuckBuildLog.fromLogContents(
        getDestPath(), Files.readAllLines(getPath(PATH_TO_BUILD_LOG), UTF_8));
  }

  public Cell asCell() throws IOException, InterruptedException {
    ProjectFilesystemAndConfig filesystemAndConfig = getProjectFilesystemAndConfig();
    ProjectFilesystem filesystem = filesystemAndConfig.projectFilesystem;
    Config config = filesystemAndConfig.config;

    ImmutableMap<String, String> env = ImmutableMap.copyOf(System.getenv());
    BuckConfig buckConfig =
        new BuckConfig(
            config,
            filesystem,
            Architecture.detect(),
            Platform.detect(),
            env,
            DefaultCellPathResolver.of(filesystem.getRootPath(), config));

    return CellProviderFactory.createForLocalBuild(
            filesystem,
            WatchmanFactory.NULL_WATCHMAN,
            buckConfig,
            CellConfig.of(),
            BuckPluginManagerFactory.createPluginManager(),
            env,
            processExecutor,
            new ExecutableFinder(),
            new DefaultProjectFilesystemFactory())
        .getCellByPath(filesystem.getRootPath());
  }

  public BuildTarget newBuildTarget(String fullyQualifiedName)
      throws IOException, InterruptedException {
    return BuildTargetFactory.newInstance(
        asCell().getFilesystem().getRootPath(), fullyQualifiedName);
  }

  public void assertFilesEqual(Path expected, Path actual) throws IOException {
    if (!expected.isAbsolute()) {
      expected = templatePath.resolve(expected);
    }
    if (!actual.isAbsolute()) {
      actual = destPath.resolve(actual);
    }
    if (!Files.isRegularFile(actual)) {
      fail("Expected file " + actual + " could not be found.");
    }

    String extension = MorePaths.getFileExtension(actual);
    String cleanPathToObservedFile =
        MoreStrings.withoutSuffix(templatePath.relativize(expected).toString(), EXPECTED_SUFFIX);

    switch (extension) {
        // For Apple .plist and .stringsdict files, we define equivalence if:
        // 1. The two files are the same type (XML or binary)
        // 2. If binary: unserialized objects are deeply-equivalent.
        //    Otherwise, fall back to exact string match.
      case "plist":
      case "stringsdict":
        NSObject expectedObject;
        try {
          expectedObject = BinaryPropertyListParser.parse(expected.toFile());
        } catch (Exception e) {
          // Not binary format.
          expectedObject = null;
        }

        NSObject observedObject;
        try {
          observedObject = BinaryPropertyListParser.parse(actual.toFile());
        } catch (Exception e) {
          // Not binary format.
          observedObject = null;
        }

        assertTrue(
            String.format(
                "In %s, expected plist to be of %s type.",
                cleanPathToObservedFile, (expectedObject != null) ? "binary" : "XML"),
            (expectedObject != null) == (observedObject != null));

        if (expectedObject != null) {
          // These keys depend on the locally installed version of Xcode, so ignore them
          // in comparisons.
          String[] ignoredKeys = {
            "DTSDKName",
            "DTPlatformName",
            "DTPlatformVersion",
            "MinimumOSVersion",
            "DTSDKBuild",
            "DTPlatformBuild",
            "DTXcode",
            "DTXcodeBuild"
          };
          if (observedObject instanceof NSDictionary && expectedObject instanceof NSDictionary) {
            for (String key : ignoredKeys) {
              ((NSDictionary) observedObject).remove(key);
              ((NSDictionary) expectedObject).remove(key);
            }
          }

          assertEquals(
              String.format(
                  "In %s, expected binary plist contents to match.", cleanPathToObservedFile),
              expectedObject,
              observedObject);
          break;
        } else {
          assertFileContentsEqual(expected, actual);
        }
        break;

      default:
        assertFileContentsEqual(expected, actual);
    }
  }

  private void assertFileContentsEqual(Path expectedFile, Path observedFile) throws IOException {
    String cleanPathToObservedFile =
        MoreStrings.withoutSuffix(
            templatePath.relativize(expectedFile).toString(), EXPECTED_SUFFIX);

    String expectedFileContent = new String(Files.readAllBytes(expectedFile), UTF_8);
    String observedFileContent = new String(Files.readAllBytes(observedFile), UTF_8);
    // It is possible, on Windows, to have Git keep "\n"-style newlines, or convert them to
    // "\r\n"-style newlines.  Support both ways by normalizing to "\n"-style newlines.
    // See https://help.github.com/articles/dealing-with-line-endings/ for more information.
    expectedFileContent = expectedFileContent.replace("\r\n", "\n");
    observedFileContent = observedFileContent.replace("\r\n", "\n");
    assertEquals(
        String.format(
            "In %s, expected content of %s to match that of %s.",
            cleanPathToObservedFile, expectedFileContent, observedFileContent),
        expectedFileContent,
        observedFileContent);
  }

  /**
   * For every file in the template directory whose name ends in {@code .expected}, checks that an
   * equivalent file has been written in the same place under the destination directory.
   *
   * @param templateSubdirectory An optional subdirectory to check. Only files in this directory
   *     will be compared.
   */
  private void assertPathsEqual(final Path templateSubdirectory, final Path destinationSubdirectory)
      throws IOException {
    SimpleFileVisitor<Path> copyDirVisitor =
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            String fileName = file.getFileName().toString();
            if (fileName.endsWith(EXPECTED_SUFFIX)) {
              // Get File for the file that should be written, but without the ".expected" suffix.
              Path generatedFileWithSuffix =
                  destinationSubdirectory.resolve(templateSubdirectory.relativize(file));
              Path directory = generatedFileWithSuffix.getParent();
              Path observedFile = directory.resolve(MorePaths.getNameWithoutExtension(file));
              assertFilesEqual(file, observedFile);
            }
            return FileVisitResult.CONTINUE;
          }
        };

    Files.walkFileTree(templateSubdirectory, copyDirVisitor);
  }

  public void verify(Path templateSubdirectory, Path destinationSubdirectory) throws IOException {
    assertPathsEqual(
        templatePath.resolve(templateSubdirectory), destPath.resolve(destinationSubdirectory));
  }

  public void verify() throws IOException {
    assertPathsEqual(templatePath, destPath);
  }

  public void verify(Path subdirectory) throws IOException {
    Preconditions.checkArgument(
        !subdirectory.isAbsolute(),
        "'verify(subdirectory)' takes a relative path, but received '%s'",
        subdirectory);
    assertPathsEqual(templatePath.resolve(subdirectory), destPath.resolve(subdirectory));
  }
}
