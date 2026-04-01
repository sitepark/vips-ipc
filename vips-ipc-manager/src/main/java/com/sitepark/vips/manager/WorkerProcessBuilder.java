package com.sitepark.vips.manager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("PMD.TooManyMethods")
class WorkerProcessBuilder {

  private static final AtomicReference<Path> cachedJarPath = new AtomicReference<>();

  private String javaExecutable;
  private List<String> jvmArgs = List.of();
  private String initialHeapSize = "32m";
  private String maximumHeapSize = "32m";
  private Path jarPath;
  private String mainClass;
  private String workerClasspath;
  private List<String> workerArgs = List.of();
  private List<String> commandOverride;
  private long commandTimeoutMs = 30_000L;
  private int concurrency;

  /**
   * Sets the path to the Java executable. Default: the current JVM process.
   */
  WorkerProcessBuilder javaExecutable(String javaExecutable) {
    this.javaExecutable = javaExecutable;
    return this;
  }

  /**
   * Sets additional JVM arguments (e.g. {@code List.of("-Xmx256m")}).
   */
  WorkerProcessBuilder jvmArgs(List<String> jvmArgs) {
    this.jvmArgs = List.copyOf(jvmArgs);
    return this;
  }

  /**
   * Sets the initial heap size ({@code -Xms}) for the worker JVM. Default: {@code "32m"}.
   *
   * @param initialHeapSize heap size string, e.g. {@code "32m"} or {@code "256m"}
   */
  WorkerProcessBuilder initialHeapSize(String initialHeapSize) {
    this.initialHeapSize = initialHeapSize;
    return this;
  }

  /**
   * Sets the maximum heap size ({@code -Xmx}) for the worker JVM. Default: {@code "32m"}.
   *
   * @param maximumHeapSize heap size string, e.g. {@code "32m"} or {@code "256m"}
   */
  WorkerProcessBuilder maximumHeapSize(String maximumHeapSize) {
    this.maximumHeapSize = maximumHeapSize;
    return this;
  }

  /**
   * Sets an explicit path to the worker JAR. Default: the embedded JAR is extracted to a temporary
   * directory.
   */
  WorkerProcessBuilder jarPath(Path jarPath) {
    this.jarPath = jarPath;
    return this;
  }

  /**
   * Sets the main class to launch instead of using {@code -jar}. When set, the worker is started
   * with {@code -cp <classpath> <mainClass>} rather than {@code -jar <jar>}. Intended for testing
   * (e.g. running the fake worker directly from the test classpath).
   */
  WorkerProcessBuilder mainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  /**
   * Sets the classpath used when {@link #mainClass(String)} is configured. Defaults to the
   * resolved JAR path when not set.
   */
  WorkerProcessBuilder workerClasspath(String workerClasspath) {
    this.workerClasspath = workerClasspath;
    return this;
  }

  /**
   * Sets additional arguments passed to the worker process after the JAR/main-class entry.
   * Default: empty.
   */
  WorkerProcessBuilder workerArgs(List<String> workerArgs) {
    this.workerArgs = List.copyOf(workerArgs);
    return this;
  }

  /**
   * Overrides the entire worker command, bypassing JAR extraction and JVM argument construction.
   * Intended for testing only (e.g. a fake worker or a crash-on-start stub).
   */
  WorkerProcessBuilder commandOverride(List<String> command) {
    this.commandOverride = List.copyOf(command);
    return this;
  }

  /**
   * Sets the timeout for individual worker commands in milliseconds. Default: 30 000 ms (30
   * seconds).
   */
  @SuppressFBWarnings("AT_NONATOMIC_64BIT_PRIMITIVE")
  WorkerProcessBuilder commandTimeoutMs(long commandTimeoutMs) {
    this.commandTimeoutMs = commandTimeoutMs;
    return this;
  }

  /**
   * Sets the number of threads vips uses for image processing. Maps to the {@code VIPS_CONCURRENCY}
   * environment variable. Default: 0 (vips uses all available CPU cores).
   */
  @SuppressFBWarnings("AT_STALE_THREAD_WRITE_OF_PRIMITIVE")
  WorkerProcessBuilder concurrency(int concurrency) {
    this.concurrency = concurrency;
    return this;
  }

  /**
   * Creates a {@link WorkerProcess}. If no JAR path has been set, the worker JAR embedded in the
   * manager JAR is extracted to a temporary directory.
   */
  WorkerProcess build() throws IOException {
    List<String> cmd;
    if (commandOverride != null) {
      cmd = commandOverride;
    } else {
      String java = resolveJavaExecutable();
      cmd =
          mainClass != null
              ? buildCommandWithMainClass(java)
              : buildCommandWithJar(java, resolveJarPath());
    }
    return new WorkerProcess(cmd, commandTimeoutMs, concurrency);
  }

  private String resolveJavaExecutable() {
    if (javaExecutable != null) {
      return javaExecutable;
    }
    return ProcessHandle.current()
        .info()
        .command()
        .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
  }

  private Path resolveJarPath() throws IOException {
    if (jarPath != null) {
      return jarPath;
    }
    Path cached = cachedJarPath.get();
    if (cached != null) {
      return cached;
    }
    Path tmp;
    try (InputStream is = WorkerProcessBuilder.class.getResourceAsStream("/vips-ipc-worker.jar")) {
      if (is == null) {
        throw new IOException(
            "Embedded worker JAR not found. Please set jarPath() or rebuild the project.");
      }
      tmp = Files.createTempFile("vips-ipc-worker-", ".jar");
      tmp.toFile().deleteOnExit();
      Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
    cachedJarPath.compareAndSet(null, tmp);
    return cachedJarPath.get();
  }

  private List<String> buildCommandWithJar(String java, Path jar) {
    List<String> cmd = new ArrayList<>();
    cmd.add(java);
    addCommonJvmArgs(cmd);
    cmd.add("-jar");
    cmd.add(jar.toAbsolutePath().toString());
    cmd.addAll(workerArgs);
    return List.copyOf(cmd);
  }

  private List<String> buildCommandWithMainClass(String java) throws IOException {
    String cp =
        workerClasspath != null ? workerClasspath : resolveJarPath().toAbsolutePath().toString();
    List<String> cmd = new ArrayList<>();
    cmd.add(java);
    addCommonJvmArgs(cmd);
    cmd.add("-cp");
    cmd.add(cp);
    cmd.add(mainClass);
    cmd.addAll(workerArgs);
    return List.copyOf(cmd);
  }

  private void addCommonJvmArgs(List<String> cmd) {
    cmd.add("--enable-native-access=ALL-UNNAMED");
    cmd.add("-Dvipsffm.autoinit=false");
    cmd.add("-Xms" + initialHeapSize);
    cmd.add("-Xmx" + maximumHeapSize);
    cmd.addAll(jvmArgs);
  }
}
