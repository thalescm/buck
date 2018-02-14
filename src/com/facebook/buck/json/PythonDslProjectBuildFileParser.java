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

package com.facebook.buck.json;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.event.PerfEventId;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.io.ProjectWatch;
import com.facebook.buck.io.WatchmanDiagnostic;
import com.facebook.buck.io.WatchmanDiagnosticEvent;
import com.facebook.buck.io.filesystem.PathOrGlobMatcher;
import com.facebook.buck.log.Logger;
import com.facebook.buck.parser.api.ProjectBuildFileParser;
import com.facebook.buck.parser.events.ParseBuckFileEvent;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.options.ProjectBuildFileParserOptions;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.util.InputStreamConsumer;
import com.facebook.buck.util.MoreSuppliers;
import com.facebook.buck.util.MoreThrowables;
import com.facebook.buck.util.ObjectMappers;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.Threads;
import com.facebook.buck.util.concurrent.AssertScopeExclusiveAccess;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.CountingInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Delegates to buck.py for parsing of buck build files. Constructed on demand for the parsing phase
 * and must be closed afterward to free up resources.
 */
public class PythonDslProjectBuildFileParser implements ProjectBuildFileParser {

  private static final String PYTHONPATH_ENV_VAR_NAME = "PYTHONPATH";

  private static final Logger LOG = Logger.get(PythonDslProjectBuildFileParser.class);

  private final ImmutableMap<String, String> environment;

  @Nullable private BuckPythonProgram buckPythonProgram;
  private Supplier<Path> rawConfigJson;
  private Supplier<Path> ignorePathsJson;

  @Nullable private ProcessExecutor.LaunchedProcess buckPyProcess;
  @Nullable private CountingInputStream buckPyProcessInput;
  @Nullable private JsonGenerator buckPyProcessJsonGenerator;
  @Nullable private JsonParser buckPyProcessJsonParser;

  private final ProjectBuildFileParserOptions options;
  private final TypeCoercerFactory typeCoercerFactory;
  private final BuckEventBus buckEventBus;
  private final ProcessExecutor processExecutor;
  private final AssertScopeExclusiveAccess assertSingleThreadedParsing;

  private boolean isInitialized;
  private boolean isClosed;

  @Nullable private FutureTask<Void> stderrConsumerTerminationFuture;
  @Nullable private Thread stderrConsumerThread;

  private AtomicReference<Path> currentBuildFile = new AtomicReference<Path>();

  public PythonDslProjectBuildFileParser(
      final ProjectBuildFileParserOptions options,
      final TypeCoercerFactory typeCoercerFactory,
      ImmutableMap<String, String> environment,
      BuckEventBus buckEventBus,
      ProcessExecutor processExecutor) {
    this.buckPythonProgram = null;
    this.options = options;
    this.typeCoercerFactory = typeCoercerFactory;
    this.environment = environment;
    this.buckEventBus = buckEventBus;
    this.processExecutor = processExecutor;
    this.assertSingleThreadedParsing = new AssertScopeExclusiveAccess();

    this.rawConfigJson =
        MoreSuppliers.memoize(
            () -> {
              try {
                Path rawConfigJson1 = Files.createTempFile("raw_config", ".json");
                Files.createDirectories(rawConfigJson1.getParent());
                try (OutputStream output =
                    new BufferedOutputStream(Files.newOutputStream(rawConfigJson1))) {
                  ObjectMappers.WRITER.writeValue(output, options.getRawConfig());
                }
                return rawConfigJson1;
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    this.ignorePathsJson =
        MoreSuppliers.memoize(
            () -> {
              try {
                Path ignorePathsJson1 = Files.createTempFile("ignore_paths", ".json");
                Files.createDirectories(ignorePathsJson1.getParent());
                try (OutputStream output =
                    new BufferedOutputStream(Files.newOutputStream(ignorePathsJson1))) {
                  ObjectMappers.WRITER.writeValue(
                      output,
                      options
                          .getIgnorePaths()
                          .stream()
                          .map(PathOrGlobMatcher::getPathOrGlob)
                          .collect(ImmutableList.toImmutableList()));
                }
                return ignorePathsJson1;
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  @VisibleForTesting
  public boolean isClosed() {
    return isClosed;
  }

  private void ensureNotClosed() {
    Preconditions.checkState(!isClosed);
  }

  /**
   * Initialization on demand moves around the performance impact of creating the Python interpreter
   * to when parsing actually begins. This makes it easier to attribute this time to the actual
   * parse phase.
   */
  @VisibleForTesting
  public void initIfNeeded() throws IOException {
    ensureNotClosed();
    if (!isInitialized) {
      init();
      isInitialized = true;
    }
  }

  /** Initialize the parser, starting buck.py. */
  private void init() throws IOException {
    try (SimplePerfEvent.Scope scope =
        SimplePerfEvent.scope(buckEventBus, PerfEventId.of("ParserInit"))) {

      ImmutableMap.Builder<String, String> pythonEnvironmentBuilder = ImmutableMap.builder();
      // Strip out PYTHONPATH. buck.py manually sets this to include only nailgun. We don't want
      // to inject nailgun into the parser's PYTHONPATH, so strip that value out.
      // If we wanted to pass on some environmental PYTHONPATH, we would have to do some actual
      // merging of this and the BuckConfig's python module search path.
      pythonEnvironmentBuilder.putAll(
          Maps.filterKeys(environment, k -> !PYTHONPATH_ENV_VAR_NAME.equals(k)));

      if (options.getPythonModuleSearchPath().isPresent()) {
        pythonEnvironmentBuilder.put(
            PYTHONPATH_ENV_VAR_NAME, options.getPythonModuleSearchPath().get());
      }

      ImmutableMap<String, String> pythonEnvironment = pythonEnvironmentBuilder.build();

      ProcessExecutorParams params =
          ProcessExecutorParams.builder()
              .setCommand(buildArgs())
              .setEnvironment(pythonEnvironment)
              .build();

      LOG.debug(
          "Starting buck.py command: %s environment: %s",
          params.getCommand(), params.getEnvironment());
      buckPyProcess = processExecutor.launchProcess(params);
      LOG.debug("Started process %s successfully", buckPyProcess);
      buckPyProcessInput = new CountingInputStream(buckPyProcess.getInputStream());
      buckPyProcessJsonGenerator = ObjectMappers.createGenerator(buckPyProcess.getOutputStream());
      // We have to wait to create the JsonParser until after we write our
      // first request, because Jackson "helpfully" synchronously reads
      // from the InputStream trying to detect whether the encoding is
      // UTF-8 or UTF-16 as soon as you create a JsonParser:
      //
      // https://git.io/vSgnA
      //
      // Since buck.py doesn't write any data until after it receives
      // a query, creating the JsonParser here would hang indefinitely.

      InputStream stderr = buckPyProcess.getErrorStream();

      AtomicInteger numberOfLines = new AtomicInteger(0);
      AtomicReference<Path> lastPath = new AtomicReference<Path>();
      InputStreamConsumer stderrConsumer =
          new InputStreamConsumer(
              stderr,
              (InputStreamConsumer.Handler)
                  line -> {
                    Path path = currentBuildFile.get();
                    if (!Objects.equals(path, lastPath.get())) {
                      numberOfLines.set(0);
                      lastPath.set(path);
                    }
                    int count = numberOfLines.getAndIncrement();
                    if (count == 0) {
                      buckEventBus.post(
                          ConsoleEvent.warning("WARNING: Output when parsing %s:", path));
                    }
                    buckEventBus.post(ConsoleEvent.warning("| %s", line));
                  });
      stderrConsumerTerminationFuture = new FutureTask<>(stderrConsumer);
      stderrConsumerThread =
          Threads.namedThread(
              PythonDslProjectBuildFileParser.class.getSimpleName(),
              stderrConsumerTerminationFuture);
      stderrConsumerThread.start();
    }
  }

  private ImmutableList<String> buildArgs() throws IOException {
    // Invoking buck.py and read JSON-formatted build rules from its stdout.
    ImmutableList.Builder<String> argBuilder = ImmutableList.builder();

    argBuilder.add(options.getPythonInterpreter());

    // Ask python to unbuffer stdout so that we can coordinate based on the output as it is
    // produced.
    argBuilder.add("-u");

    argBuilder.add(getPathToBuckPy(options.getDescriptions()).toString());

    if (options.getEnableProfiling()) {
      argBuilder.add("--profile");
    }

    if (options.getAllowEmptyGlobs()) {
      argBuilder.add("--allow_empty_globs");
    }

    if (options.getUseWatchmanGlob()) {
      argBuilder.add("--use_watchman_glob");
    }

    if (options.getWatchmanGlobStatResults()) {
      argBuilder.add("--watchman_glob_stat_results");
    }

    if (options.getWatchmanUseGlobGenerator()) {
      argBuilder.add("--watchman_use_glob_generator");
    }

    if (options.getWatchman().getTransportPath().isPresent()) {
      argBuilder.add(
          "--watchman_socket_path",
          options.getWatchman().getTransportPath().get().toAbsolutePath().toString());
    }

    if (options.getWatchmanQueryTimeoutMs().isPresent()) {
      argBuilder.add(
          "--watchman_query_timeout_ms", options.getWatchmanQueryTimeoutMs().get().toString());
    }

    // Add the --build_file_import_whitelist flags.
    for (String module : options.getBuildFileImportWhitelist()) {
      argBuilder.add("--build_file_import_whitelist");
      argBuilder.add(module);
    }

    argBuilder.add("--project_root", options.getProjectRoot().toAbsolutePath().toString());

    for (ImmutableMap.Entry<String, Path> entry : options.getCellRoots().entrySet()) {
      argBuilder.add("--cell_root", entry.getKey() + "=" + entry.getValue());
    }

    argBuilder.add("--cell_name", options.getCellName());

    argBuilder.add("--build_file_name", options.getBuildFileName());

    // Tell the parser not to print exceptions to stderr.
    argBuilder.add("--quiet");

    // Add the --include flags.
    for (String include : options.getDefaultIncludes()) {
      argBuilder.add("--include");
      argBuilder.add(include);
    }

    // Add all config settings.
    argBuilder.add("--config", rawConfigJson.get().toString());

    // Add ignore paths.
    argBuilder.add("--ignore_paths", ignorePathsJson.get().toString());

    // Disable native rules if requested
    if (options.getDisableImplicitNativeRules()) {
      argBuilder.add("--disable_implicit_native_rules");
    }

    if (options.isWarnAboutDeprecatedSyntax()) {
      argBuilder.add("--warn_about_deprecated_syntax");
    }

    return argBuilder.build();
  }

  /**
   * Collect all rules from a particular build file.
   *
   * @param buildFile should be an absolute path to a build file. Must have rootPath as its prefix.
   */
  @Override
  public ImmutableList<Map<String, Object>> getAll(Path buildFile, AtomicLong processedBytes)
      throws BuildFileParseException, InterruptedException {
    ImmutableList<Map<String, Object>> result = getAllRulesAndMetaRules(buildFile, processedBytes);

    // Strip out the __includes, __configs, and __env meta rules, which are the last rules.
    return result.subList(0, result.size() - 3);
  }

  /**
   * Collect all rules from a particular build file, along with meta rules about the rules, for
   * example which build files the rules depend on.
   *
   * @param buildFile should be an absolute path to a build file. Must have rootPath as its prefix.
   */
  @Override
  public ImmutableList<Map<String, Object>> getAllRulesAndMetaRules(
      Path buildFile, AtomicLong processedBytes)
      throws BuildFileParseException, InterruptedException {
    try {
      return getAllRulesInternal(buildFile, processedBytes);
    } catch (IOException e) {
      LOG.warn(e, "Error getting all rules for %s", buildFile);
      MoreThrowables.propagateIfInterrupt(e);
      throw BuildFileParseException.createForBuildFileParseError(buildFile, e);
    }
  }

  @VisibleForTesting
  protected ImmutableList<Map<String, Object>> getAllRulesInternal(
      Path buildFile, AtomicLong processedBytes) throws IOException, BuildFileParseException {
    ensureNotClosed();
    initIfNeeded();

    // Check isInitialized implications (to avoid Eradicate warnings).
    Preconditions.checkNotNull(buckPyProcess);
    Preconditions.checkNotNull(buckPyProcessInput);
    long alreadyReadBytes = buckPyProcessInput.getCount();

    ParseBuckFileEvent.Started parseBuckFileStarted = ParseBuckFileEvent.started(buildFile);
    buckEventBus.post(parseBuckFileStarted);

    ImmutableList<Map<String, Object>> values = ImmutableList.of();
    Optional<String> profile = Optional.empty();
    try (AssertScopeExclusiveAccess.Scope scope = assertSingleThreadedParsing.scope()) {
      Path cellPath = options.getProjectRoot().toAbsolutePath();
      String watchRoot = cellPath.toString();
      String projectPrefix = "";
      if (options.getWatchman().getProjectWatches().containsKey(cellPath)) {
        ProjectWatch projectWatch = options.getWatchman().getProjectWatches().get(cellPath);
        watchRoot = projectWatch.getWatchRoot();
        if (projectWatch.getProjectPrefix().isPresent()) {
          projectPrefix = projectWatch.getProjectPrefix().get();
        }
      }
      currentBuildFile.set(buildFile);
      BuildFilePythonResult resultObject =
          performJsonRequest(
              ImmutableMap.of(
                  "buildFile", buildFile.toString(),
                  "watchRoot", watchRoot,
                  "projectPrefix", projectPrefix));
      Path buckPyPath = getPathToBuckPy(options.getDescriptions());
      handleDiagnostics(
          buildFile, buckPyPath.getParent(), resultObject.getDiagnostics(), buckEventBus);
      values = resultObject.getValues();

      LOG.verbose("Got rules: %s", values);
      LOG.verbose("Parsed %d rules from %s", values.size(), buildFile);
      profile = resultObject.getProfile();
      if (profile.isPresent()) {
        LOG.debug("Profile result:\n%s", profile.get());
      }
      return values;
    } finally {
      long parsedBytes = buckPyProcessInput.getCount() - alreadyReadBytes;
      processedBytes.addAndGet(parsedBytes);
      buckEventBus.post(
          ParseBuckFileEvent.finished(parseBuckFileStarted, values, parsedBytes, profile));
    }
  }

  private BuildFilePythonResult performJsonRequest(ImmutableMap<String, String> request)
      throws IOException {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(buckPyProcessJsonGenerator);
    buckPyProcessJsonGenerator.writeObject(request);
    try {
      // We disable autoflush at the ObjectMapper level for
      // performance reasons, but our protocol requires us to
      // flush newline-delimited JSON for each buck.py query.
      buckPyProcessJsonGenerator.flush();
      // I tried using MinimalPrettyPrinter.setRootValueSeparator("\n") and
      // setting it on the JsonGenerator, but it doesn't seem to
      // actually write a newline after each element.
      Preconditions.checkNotNull(buckPyProcess);
      buckPyProcess.getOutputStream().write('\n');
      // I tried enabling JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM,
      // but it doesn't actually flush.
      buckPyProcess.getOutputStream().flush();
    } catch (IOException e) {
      // https://issues.apache.org/jira/browse/EXEC-101 -- Java 8 throws
      // IOException if the child process exited before writing/flushing
      LOG.debug(e, "Swallowing exception on flush");
    }

    if (buckPyProcessJsonParser == null) {
      // We have to wait to create the JsonParser until after we write our
      // first request, because Jackson "helpfully" synchronously reads
      // from the InputStream trying to detect whether the encoding is
      // UTF-8 or UTF-16 as soon as you create a JsonParser:
      //
      // https://git.io/vSgnA
      //
      // Since buck.py doesn't write any data until after it receives
      // a query, creating the JsonParser any earlier than this would
      // hang indefinitely.
      Preconditions.checkNotNull(buckPyProcessInput);
      buckPyProcessJsonParser = ObjectMappers.createParser(buckPyProcessInput);
    }
    LOG.verbose("Parsing output of process %s...", buckPyProcess);
    BuildFilePythonResult resultObject;
    try {
      resultObject = buckPyProcessJsonParser.readValueAs(BuildFilePythonResult.class);
    } catch (IOException e) {
      LOG.warn(e, "Parser exited while decoding JSON data");
      throw e;
    }
    return resultObject;
  }

  private static void handleDiagnostics(
      Path buildFile,
      Path buckPyDir,
      List<Map<String, Object>> diagnosticsList,
      BuckEventBus buckEventBus)
      throws IOException, BuildFileParseException {
    for (Map<String, Object> diagnostic : diagnosticsList) {
      String level = (String) diagnostic.get("level");
      String message = (String) diagnostic.get("message");
      String source = (String) diagnostic.get("source");
      if (level == null || message == null) {
        throw new IOException(
            String.format(
                "Invalid diagnostic(level=%s, message=%s, source=%s)", level, message, source));
      }
      if (source != null && source.equals("watchman")) {
        handleWatchmanDiagnostic(buildFile, level, message, buckEventBus);
      } else {
        String header;
        if (source != null) {
          header = buildFile + " (" + source + ")";
        } else {
          header = buildFile.toString();
        }
        switch (level) {
          case "debug":
            LOG.debug("%s: %s", header, message);
            break;
          case "info":
            LOG.info("%s: %s", header, message);
            break;
          case "warning":
            LOG.warn("Warning raised by BUCK file parser for file %s: %s", header, message);
            buckEventBus.post(
                ConsoleEvent.warning("Warning raised by BUCK file parser: %s", message));
            break;
          case "error":
            LOG.warn("Error raised by BUCK file parser for file %s: %s", header, message);
            buckEventBus.post(ConsoleEvent.severe("Error raised by BUCK file parser: %s", message));
            break;
          case "fatal":
            LOG.warn("Fatal error raised by BUCK file parser for file %s: %s", header, message);
            Object exception = diagnostic.get("exception");
            throw BuildFileParseException.createForBuildFileParseError(
                buildFile, createParseException(buildFile, buckPyDir, message, exception));
          default:
            LOG.warn(
                "Unknown diagnostic (level %s) raised by BUCK file parser for build file %s: %s",
                level, buildFile, message);
            break;
        }
      }
    }
  }

  private static Optional<BuildFileSyntaxError> parseSyntaxError(Map<String, Object> exceptionMap) {
    String type = (String) exceptionMap.get("type");
    if ("SyntaxError".equals(type)) {
      return Optional.of(
          BuildFileSyntaxError.of(
              Paths.get((String) Preconditions.checkNotNull(exceptionMap.get("filename"))),
              (Number) Preconditions.checkNotNull(exceptionMap.get("lineno")),
              Optional.ofNullable((Number) exceptionMap.get("offset")),
              (String) Preconditions.checkNotNull(exceptionMap.get("text"))));
    } else {
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private static ImmutableList<BuildFileParseExceptionStackTraceEntry> parseStackTrace(
      Map<String, Object> exceptionMap) {
    List<Map<String, Object>> traceback =
        (List<Map<String, Object>>) Preconditions.checkNotNull(exceptionMap.get("traceback"));
    ImmutableList.Builder<BuildFileParseExceptionStackTraceEntry> stackTraceBuilder =
        ImmutableList.builder();
    for (Map<String, Object> tracebackItem : traceback) {
      stackTraceBuilder.add(
          BuildFileParseExceptionStackTraceEntry.of(
              Paths.get((String) Preconditions.checkNotNull(tracebackItem.get("filename"))),
              (Number) Preconditions.checkNotNull(tracebackItem.get("line_number")),
              (String) Preconditions.checkNotNull(tracebackItem.get("function_name")),
              (String) Preconditions.checkNotNull(tracebackItem.get("text"))));
    }
    return stackTraceBuilder.build();
  }

  @VisibleForTesting
  static BuildFileParseExceptionData parseExceptionData(Map<String, Object> exceptionMap) {
    return BuildFileParseExceptionData.of(
        (String) Preconditions.checkNotNull(exceptionMap.get("type")),
        (String) Preconditions.checkNotNull(exceptionMap.get("value")),
        parseSyntaxError(exceptionMap),
        parseStackTrace(exceptionMap));
  }

  private static String formatStackTrace(
      Path buckPyDir, ImmutableList<BuildFileParseExceptionStackTraceEntry> stackTrace) {
    StringBuilder formattedTraceback = new StringBuilder();
    for (BuildFileParseExceptionStackTraceEntry entry : stackTrace) {
      if (entry.getFileName().getParent().equals(buckPyDir)) {
        // Skip stack trace entries for buck.py itself
        continue;
      }
      String location;
      if (entry.getFunctionName().equals("<module>")) {
        location = "";
      } else {
        location = String.format(", in %s", entry.getFunctionName());
      }
      formattedTraceback.append(
          String.format(
              "  File \"%s\", line %s%s\n    %s\n",
              entry.getFileName(), entry.getLineNumber(), location, entry.getText()));
    }
    return formattedTraceback.toString();
  }

  @SuppressWarnings("unchecked")
  private static IOException createParseException(
      Path buildFile, Path buckPyDir, String message, @Nullable Object exception) {
    if (!(exception instanceof Map<?, ?>)) {
      return new IOException(message);
    } else {
      Map<String, Object> exceptionMap = (Map<String, Object>) exception;
      BuildFileParseExceptionData exceptionData = parseExceptionData(exceptionMap);
      LOG.debug("Received exception from buck.py parser: %s", exceptionData);
      Optional<BuildFileSyntaxError> syntaxErrorOpt = exceptionData.getSyntaxError();
      if (syntaxErrorOpt.isPresent()) {
        BuildFileSyntaxError syntaxError = syntaxErrorOpt.get();
        String errorMsg = "";
        if (buildFile.equals(syntaxError.getFileName())) {
          // BuildFileParseException will include the filename
          errorMsg += String.format("Syntax error on line %s", syntaxError.getLineNumber());
        } else {
          // Parse error was in some other file included by the build file
          errorMsg +=
              String.format(
                  "Syntax error in %s\nLine %s",
                  syntaxError.getFileName(), syntaxError.getLineNumber());
        }
        if (syntaxError.getOffset().isPresent()) {
          errorMsg += String.format(", column %s", syntaxError.getOffset().get());
        }
        errorMsg += ":\n" + syntaxError.getText();
        if (syntaxError.getOffset().isPresent()) {
          errorMsg += Strings.padStart("^", syntaxError.getOffset().get().intValue(), ' ');
        }
        return new IOException(errorMsg);
      } else if (exceptionData.getType().equals("IncorrectArgumentsException")) {
        return new IOException(message);
      } else {
        String formattedStackTrace = formatStackTrace(buckPyDir, exceptionData.getStackTrace());
        return new IOException(
            String.format(
                "%s: %s\nCall stack:\n%s",
                exceptionData.getType(), exceptionData.getValue(), formattedStackTrace));
      }
    }
  }

  private static void handleWatchmanDiagnostic(
      Path buildFile, String level, String message, BuckEventBus buckEventBus) throws IOException {
    WatchmanDiagnostic.Level watchmanDiagnosticLevel;
    switch (level) {
        // Watchman itself doesn't issue debug or info, but in case
        // engineers hacking on stuff add calls, let's log them
        // then return.
      case "debug":
        LOG.debug("%s (watchman): %s", buildFile, message);
        return;
      case "info":
        LOG.info("%s (watchman): %s", buildFile, message);
        return;
      case "warning":
        watchmanDiagnosticLevel = WatchmanDiagnostic.Level.WARNING;
        break;
      case "error":
        watchmanDiagnosticLevel = WatchmanDiagnostic.Level.ERROR;
        break;
      case "fatal":
        throw new IOException(String.format("%s (watchman): %s", buildFile, message));
      default:
        throw new RuntimeException(
            String.format(
                "Unrecognized watchman diagnostic level: %s (message=%s)", level, message));
    }
    WatchmanDiagnostic watchmanDiagnostic = WatchmanDiagnostic.of(watchmanDiagnosticLevel, message);
    buckEventBus.post(new WatchmanDiagnosticEvent(watchmanDiagnostic));
  }

  @Override
  public void reportProfile() throws IOException {
    BuildFilePythonResult resultObject =
        performJsonRequest(ImmutableMap.of("command", "report_profile"));
    Optional<String> profile = resultObject.getProfile();
    if (profile.isPresent()) {
      LOG.debug("buck parser profiler trace available");
      buckEventBus.post(ParseBuckProfilerReportEvent.profilerReport(profile.get()));
    }
  }

  @Override
  @SuppressWarnings("PMD.EmptyCatchBlock")
  public void close() throws BuildFileParseException, InterruptedException, IOException {
    if (isClosed) {
      return;
    }

    try {
      if (isInitialized) {

        // Check isInitialized implications (to avoid Eradicate warnings).
        Preconditions.checkNotNull(buckPyProcess);

        // Allow buck.py to terminate gracefully.
        if (buckPyProcessJsonGenerator != null) {
          try {
            LOG.debug("Closing buck.py process stdin");
            // Closing the JSON generator has the side effect of closing stdin,
            // which lets buck.py terminate gracefully.
            buckPyProcessJsonGenerator.close();
          } catch (IOException e) {
            // Safe to ignore since we've already flushed everything we wanted
            // to write.
          } finally {
            buckPyProcessJsonGenerator = null;
          }
        }

        if (buckPyProcessJsonParser != null) {
          try {
            buckPyProcessJsonParser.close();
          } catch (IOException e) {
          } finally {
            buckPyProcessJsonParser = null;
          }
        }

        if (stderrConsumerThread != null) {
          stderrConsumerThread.join();
          stderrConsumerThread = null;
          try {
            Preconditions.checkNotNull(stderrConsumerTerminationFuture).get();
          } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
              throw (IOException) cause;
            } else {
              throw new RuntimeException(e);
            }
          }
          stderrConsumerTerminationFuture = null;
        }

        LOG.debug("Waiting for process %s to exit...", buckPyProcess);
        ProcessExecutor.Result result = processExecutor.waitForLaunchedProcess(buckPyProcess);
        if (result.getExitCode() != 0) {
          LOG.warn(result.getMessageForUnexpectedResult(buckPyProcess.toString()));
          throw BuildFileParseException.createForUnknownParseError(
              result.getMessageForResult("Parser did not exit cleanly"));
        }
        LOG.debug("Process %s exited cleanly.", buckPyProcess);

        try {
          synchronized (this) {
            if (buckPythonProgram != null) {
              buckPythonProgram.close();
            }
          }
        } catch (IOException e) {
          // Eat any exceptions from deleting the temporary buck.py file.
        }
      }
    } finally {
      isClosed = true;
    }
  }

  private synchronized Path getPathToBuckPy(ImmutableSet<Description<?>> descriptions)
      throws IOException {
    if (buckPythonProgram == null) {
      buckPythonProgram =
          BuckPythonProgram.newInstance(
              typeCoercerFactory, descriptions, !options.getEnableProfiling());
    }
    return buckPythonProgram.getExecutablePath();
  }
}
