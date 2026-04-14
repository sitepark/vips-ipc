package com.sitepark.vips.manager;

import com.sitepark.vips.worker.HandlerRegistryDefaultFactory;
import com.sitepark.vips.worker.HandlerRegistryFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("PMD.TooManyMethods")
public class VipsClientBuilder {

  private static final int MIN_POOL_SIZE = 1;

  private final WorkerProcessBuilder processBuilder = new WorkerProcessBuilder();
  private HandlerRegistryFactory handlerRegistryFactory;

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
   * Sets the OS scheduling priority adjustment for the worker process via {@code nice -n <value>}.
   * Range 1–19 lowers priority (19 = lowest). Negative values raise priority and typically require
   * root. Value 0 (default) disables the {@code nice} prefix entirely.
   *
   * <p>The {@code nice} prefix is only applied on non-Windows systems. On Windows the setting is
   * silently ignored.
   */
  public VipsClientBuilder niceLevel(int niceLevel) {
    processBuilder.niceLevel(niceLevel);
    return this;
  }

  /**
   * Enables in-process mode: commands are dispatched directly to the handler registry in the
   * current JVM instead of spawning a worker subprocess. Intended for debugging with a Java
   * debugger that cannot attach to child JVM processes.
   *
   * <p>In this mode all subprocess-related settings (JAR path, JVM arguments, concurrency, nice
   * level, timeout) are ignored. Requires libvips to be installed and accessible to the current
   * JVM.
   */
  public VipsClientBuilder inProcess() {
    this.handlerRegistryFactory = new HandlerRegistryDefaultFactory("in-process");
    return this;
  }

  /**
   * Enables in-process mode: commands are dispatched directly to the handler registry in the
   * current JVM instead of spawning a worker subprocess. Intended for debugging with a Java
   * debugger that cannot attach to child JVM processes.
   *
   * <p>In this mode all subprocess-related settings (JAR path, JVM arguments, concurrency, nice
   * level, timeout) are ignored. Requires libvips to be installed and accessible to the current
   * JVM.
   */
  @SuppressWarnings("PMD.NullAssignment")
  public VipsClientBuilder inProcess(boolean inProcess) {
    if (inProcess) {
      return this.inProcess();
    }
    this.handlerRegistryFactory = null;
    return this;
  }

  /**
   * Creates a {@link VipsClient}. If no JAR path has been set, the worker JAR embedded in the
   * manager JAR is extracted to a temporary directory.
   */
  public VipsClient build() throws IOException {
    if (handlerRegistryFactory != null) {
      return new VipsClient(new InProcessWorkerBackend(handlerRegistryFactory));
    }
    return new VipsClient(processBuilder.build());
  }

  /**
   * Creates a {@link VipsClientPool} backed by {@code poolSize} independent worker processes.
   *
   * <p>All builder settings (JAR path, JVM args, concurrency, timeout, etc.) are applied to every
   * worker in the pool. If any worker fails to start, all previously started workers are shut down
   * before the exception propagates.
   *
   * <p>Recommended usage for CPU-bound image processing on an N-core machine:
   *
   * <pre>{@code
   * int cores = Runtime.getRuntime().availableProcessors();
   * try (VipsClientPool pool = VipsClient.builder().buildPool(cores)) {
   *   files.parallelStream().forEach(f -> {
   *     try { pool.resize(f, output, 0.5); } catch (IOException e) { ... }
   *   });
   * }
   * }</pre>
   *
   * <p>For maximum throughput with many small images (codec-heavy workloads), reduce per-worker
   * VIPS threads and increase pool size:
   *
   * <pre>{@code
   * VipsClient.builder().concurrency(1).buildPool(Runtime.getRuntime().availableProcessors())
   * }</pre>
   *
   * @param poolSize number of worker processes; must be &gt;= 1
   * @throws IllegalArgumentException if {@code poolSize} is less than 1
   * @throws IOException              if any worker process cannot be started (e.g. embedded JAR extraction
   *                                  fails)
   */
  @SuppressWarnings("PMD.CloseResource")
  public VipsClientPool buildPool(int poolSize) throws IOException {
    if (poolSize < MIN_POOL_SIZE) {
      throw new IllegalArgumentException("poolSize must be >= 1, got: " + poolSize);
    }
    if (handlerRegistryFactory != null) {
      List<WorkerBackend> workers = new ArrayList<>(poolSize);
      for (int i = 0; i < poolSize; i++) {
        workers.add(new InProcessWorkerBackend(handlerRegistryFactory));
      }
      return new VipsClientPool(workers);
    }
    List<WorkerBackend> workers = new ArrayList<>(poolSize);
    try {
      for (int i = 0; i < poolSize; i++) {
        workers.add(processBuilder.build());
      }
    } catch (IOException e) {
      for (WorkerBackend worker : workers) {
        worker.close();
      }
      throw e;
    }
    return new VipsClientPool(workers);
  }
}
