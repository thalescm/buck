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

package com.facebook.buck.jvm.kotlin;

import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public class KotlinBuckConfig {

  private static final String SECTION = "kotlin";

  private static final Path DEFAULT_KOTLIN_COMPILER = Paths.get("kotlinc");

  private final BuckConfig delegate;
  private @Nullable
  Path kotlinHome;

  public KotlinBuckConfig(BuckConfig delegate) {
    this.delegate = delegate;
  }

  public Kotlinc getKotlinc() {
    if (isExternalCompilation()) {
      return new ExternalKotlinc(getPathToCompilerBinary());
    } else {
      ImmutableSet<SourcePath> classpathEntries =
          ImmutableSet.of(
              delegate.getPathSourcePath(getPathToStdlibJar()),
              delegate.getPathSourcePath(getPathToReflectJar()),
              delegate.getPathSourcePath(getPathToScriptRuntimeJar()),
              delegate.getPathSourcePath(getPathToCompilerJar()));

      return new JarBackedReflectedKotlinc(
          classpathEntries,
          getPathToAP(),
          getPathToStdlibJar(),
          getPathToTools());
    }
  }

  Path getPathToCompilerBinary() {
    Path compilerPath = getKotlinHome().resolve("kotlinc");
    if (!Files.isExecutable(compilerPath)) {
      compilerPath = getKotlinHome().resolve(Paths.get("bin", "kotlinc"));
      if (!Files.isExecutable(compilerPath)) {
        throw new HumanReadableException("Could not resolve kotlinc location.");
      }
    }

    return new ExecutableFinder().getExecutable(compilerPath, delegate.getEnvironment());
  }

  private Path getPathToJar(String jarName) {
    Path reflect = getKotlinHome().resolve(jarName + ".jar");
    if (Files.isRegularFile(reflect)) {
      return reflect.normalize();
    }

    reflect = getKotlinHome().resolve(Paths.get("lib", jarName + ".jar"));
    if (Files.isRegularFile(reflect)) {
      return reflect.normalize();
    }

    reflect = getKotlinHome().resolve(Paths.get("libexec", "lib", jarName + ".jar"));
    if (Files.isRegularFile(reflect)) {
      return reflect.normalize();
    }

    throw new HumanReadableException(
        "Could not resolve " + jarName + " JAR location (kotlin home:" + getKotlinHome() + ").");
  }

  /**
   * Get the path to the Kotlin runtime jar.
   *
   * @return the Kotlin runtime jar path
   */
  Path getPathToStdlibJar() {
    try {
      return getPathToJar("kotlin-stdlib");
    } catch (HumanReadableException e) {
      // TODO: Check if kt version < 1.1
      return getPathToJar("kotlin-runtime");
    }
  }

  /**
   * Get the path to the Kotlin reflection jar.
   *
   * @return the Kotlin reflection jar path
   */
  Path getPathToReflectJar() {
    return getPathToJar("kotlin-reflect");
  }

  /**
   * Get the path to the Kotlin script runtime jar.
   *
   * @return the Kotlin script runtime jar path
   */
  Path getPathToScriptRuntimeJar() {
    return getPathToJar("kotlin-script-runtime");
  }

  /**
   * Get the path to the Kotlin compiler jar.
   *
   * @return the Kotlin compiler jar path
   */
  Path getPathToCompilerJar() {
    return getPathToJar("kotlin-compiler");
  }

  /**
   * Get the path to the Kotlin annotation processing jar.
   *
   * @return the Kotlin annotation processing jar path
   */
  Path getPathToAP() {
    return getPathToJar("kotlin-annotation-processing");
  }

  /**
   * Get the path to the java tools jar.
   *
   * @return the java tools jar path
   */
  Path getPathToTools() {
    Path compiler = getJavaHome().resolve("lib/tools.jar");
    if (Files.isRegularFile(compiler)) {
      return compiler.normalize();
    }

    throw new HumanReadableException(
        "Could not resolve tools JAR location (java home:" + getJavaHome() + ").");
  }

  /**
   * Determine whether external Kotlin compilation is being forced. The default is internal
   * (in-process) execution, but this can be overridden in .buckconfig by setting the "external"
   * property to "true".
   *
   * @return true is external compilation is requested, false otherwise
   */
  private boolean isExternalCompilation() {
    Optional<Boolean> value = delegate.getBoolean(SECTION, "external");
    return value.orElse(false);
  }

  /**
   * Find the Kotlin home (installation) directory by searching in this order: <br>
   *
   * <ul>
   * <li>If the "kotlin_home" directory is specified in .buckconfig then use it.
   * <li>Check the environment for a KOTLIN_HOME variable, if defined then use it.
   * <li>Resolve "kotlinc" with an ExecutableFinder, and if found then deduce the kotlin home
   * directory from it.
   * </ul>
   *
   * @return the Kotlin home path
   */
  private Path getKotlinHome() {
    if (kotlinHome != null) {
      return kotlinHome;
    }

    try {
      // Check the buck configuration for a specified kotlin home
      Optional<String> value = delegate.getValue(SECTION, "kotlin_home");

      if (value.isPresent()) {
        boolean isAbsolute = Paths.get(value.get()).isAbsolute();
        Optional<Path> homePath = delegate.getPath(SECTION, "kotlin_home", !isAbsolute);
        if (homePath.isPresent() && Files.isDirectory(homePath.get())) {
          return homePath.get().toRealPath().normalize();
        } else {
          throw new HumanReadableException(
              "Kotlin home directory (" + homePath + ") specified in .buckconfig was not found.");
        }
      } else {
        // If the KOTLIN_HOME environment variable is specified we trust it
        String home = delegate.getEnvironment().get("KOTLIN_HOME");
        if (home != null) {
          return Paths.get(home).normalize();
        } else {
          // Lastly, we try to resolve from the system PATH
          Optional<Path> compiler =
              new ExecutableFinder()
                  .getOptionalExecutable(DEFAULT_KOTLIN_COMPILER, delegate.getEnvironment());
          if (compiler.isPresent()) {
            kotlinHome = compiler.get().toRealPath().getParent().normalize();
            if (kotlinHome != null && kotlinHome.endsWith(Paths.get("bin"))) {
              kotlinHome = kotlinHome.getParent().normalize();
            }
            return kotlinHome;
          } else {
            throw new HumanReadableException(
                "Could not resolve kotlin home directory, Consider setting KOTLIN_HOME.");
          }
        }
      }
    } catch (IOException io) {
      throw new HumanReadableException(
          "Could not resolve kotlin home directory, Consider setting KOTLIN_HOME.", io);
    }
  }

  /**
   * Find the Java home (installation) directory by searching in this order: <br>
   *
   * <ul>
   *   <li>If the "java_home" directory is specified in .buckconfig then use it.
   *   <li>Check the environment for a JAVA_HOME variable, if defined then use it.
   * </ul>
   *
   * @return the Java home path
   */
  private Path getJavaHome() {
    Map<String, String> environment = System.getenv();
    try {
      String JAVA_SECTION = "java";
      // Check the buck configuration for a specified kotlin home
      Optional<String> value = delegate.getValue(JAVA_SECTION, "java_home");
      if (value.isPresent()) {
        boolean isAbsolute = Paths.get(value.get()).isAbsolute();
        Optional<Path> homePath = delegate.getPath(JAVA_SECTION, "java_home", !isAbsolute);
        if (homePath.isPresent() && Files.isDirectory(homePath.get())) {
          return homePath.get().toRealPath().normalize();
        } else {
          throw new HumanReadableException(
              "Java home directory (" + homePath + ") specified in .buckconfig was not found.");
        }
      } else if (environment.containsKey("JAVA_HOME")) {
        return Paths.get(environment.get("JAVA_HOME")).normalize();
      } else if (System.getProperty("java.home") != null) {
        return Paths.get(System.getProperty("java.home")).normalize();
      } else {
        throw new HumanReadableException(
            "Could not resolve java home directory, Consider setting JAVA_HOME.");
      }
    } catch (IOException io) {
      throw new HumanReadableException(
          "Could not resolve java home directory, Consider setting JAVA_HOME.", io);
    }
  }
}
