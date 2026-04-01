package com.sitepark.vips.manager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("PMD.TooManyMethods")
public class VipsClientBuilder {

  private final WorkerProcessBuilder processBuilder = new WorkerProcessBuilder();

  /**
   * Sets the path to the Java executable. Default: the current JVM process.
   */
  public VipsClientBuilder javaExecutable(String javaExecutable) {
    processBuilder.javaExecutable(javaExecutable);
    return this;
  }

  /**
   * Sets additional JVM arguments (e.g. {@code List.of("-Xmx256m")}).
   */
  public VipsClientBuilder jvmArgs(List<String> jvmArgs) {
    processBuilder.jvmArgs(jvmArgs);
    return this;
  }

  /**
   * Sets the initial heap size ({@code -Xms}) for the worker JVM. Default: {@code "32m"}.
   *
   * @param initialHeapSize heap size string, e.g. {@code "32m"} or {@code "256m"}
   */
  public VipsClientBuilder initialHeapSize(String initialHeapSize) {
    processBuilder.initialHeapSize(initialHeapSize);
    return this;
  }

  /**
   * Sets the maximum heap size ({@code -Xmx}) for the worker JVM. Default: {@code "32m"}.
   *
   * @param maximumHeapSize heap size string, e.g. {@code "32m"} or {@code "256m"}
   */
  public VipsClientBuilder maximumHeapSize(String maximumHeapSize) {
    processBuilder.maximumHeapSize(maximumHeapSize);
    return this;
  }

  /**
   * Sets an explicit path to the worker JAR. Default: the embedded JAR is extracted to a temporary
   * directory.
   */
  public VipsClientBuilder jarPath(Path jarPath) {
    processBuilder.jarPath(jarPath);
    return this;
  }

  /**
   * Sets the main class to launch instead of using {@code -jar}. When set, the worker is started
   * with {@code -cp <classpath> <mainClass>} rather than {@code -jar <jar>}. Intended for testing
   * (e.g. running a fake worker directly from the test classpath).
   */
  public VipsClientBuilder mainClass(String mainClass) {
    processBuilder.mainClass(mainClass);
    return this;
  }

  /**
   * Sets the classpath used when {@link #mainClass(String)} is configured. Defaults to the
   * resolved JAR path when not set.
   */
  public VipsClientBuilder workerClasspath(String workerClasspath) {
    processBuilder.workerClasspath(workerClasspath);
    return this;
  }

  /**
   * Sets additional arguments passed to the worker process after the JAR/main-class entry.
   * Default: empty.
   */
  public VipsClientBuilder workerArgs(List<String> workerArgs) {
    processBuilder.workerArgs(workerArgs);
    return this;
  }

  /**
   * Overrides the entire worker command, bypassing JAR extraction and JVM argument construction.
   * Intended for testing only (e.g. a fake worker or a crash-on-start stub).
   */
  public VipsClientBuilder commandOverride(List<String> command) {
    processBuilder.commandOverride(command);
    return this;
  }

  /**
   * Sets the timeout for individual worker commands in milliseconds. Default: 30 000 ms (30
   * seconds).
   */
  public VipsClientBuilder commandTimeoutMs(long commandTimeoutMs) {
    processBuilder.commandTimeoutMs(commandTimeoutMs);
    return this;
  }

  /**
   * Sets the number of threads vips uses for image processing. Maps to the {@code VIPS_CONCURRENCY}
   * environment variable. Default: 0 (vips uses all available CPU cores).
   */
  public VipsClientBuilder concurrency(int concurrency) {
    processBuilder.concurrency(concurrency);
    return this;
  }

  /**
   * Creates a {@link VipsClient}. If no JAR path has been set, the worker JAR embedded in the
   * manager JAR is extracted to a temporary directory.
   */
  public VipsClient build() throws IOException {
    return new VipsClient(processBuilder.build());
  }
}
