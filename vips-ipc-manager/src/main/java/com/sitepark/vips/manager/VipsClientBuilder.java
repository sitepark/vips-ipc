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

public class VipsClientBuilder {

  private static final AtomicReference<Path> cachedJarPath = new AtomicReference<>();

  private String javaExecutable;
  private List<String> jvmArgs = List.of();
  private Path jarPath;
  private long commandTimeoutMs = 30_000L;

  /**
   * Sets the path to the Java executable. Default: the current JVM process.
   */
  public VipsClientBuilder javaExecutable(String javaExecutable) {
    this.javaExecutable = javaExecutable;
    return this;
  }

  /**
   * Sets additional JVM arguments (e.g. {@code List.of("-Xmx256m")}).
   */
  public VipsClientBuilder jvmArgs(List<String> jvmArgs) {
    this.jvmArgs = List.copyOf(jvmArgs);
    return this;
  }

  /**
   * Sets an explicit path to the worker JAR. Default: the embedded JAR is extracted to a temporary
   * directory.
   */
  public VipsClientBuilder jarPath(Path jarPath) {
    this.jarPath = jarPath;
    return this;
  }

  /**
   * Sets the timeout for individual worker commands in milliseconds. Default: 30 000 ms (30
   * seconds).
   */
  @SuppressFBWarnings("AT_NONATOMIC_64BIT_PRIMITIVE")
  public VipsClientBuilder commandTimeoutMs(long commandTimeoutMs) {
    this.commandTimeoutMs = commandTimeoutMs;
    return this;
  }

  /**
   * Creates a {@link VipsClient}. If no JAR path has been set, the worker JAR embedded in the
   * manager JAR is extracted to a temporary directory.
   */
  public VipsClient build() throws IOException {
    String java = resolveJavaExecutable();
    Path jar = resolveJarPath();
    return new VipsClient(buildCommand(java, jar), commandTimeoutMs);
  }

  private String resolveJavaExecutable() {
    if (javaExecutable != null) {
      return javaExecutable;
    }
    return ProcessHandle.current().info().command().orElse("java");
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
    try (InputStream is = VipsClientBuilder.class.getResourceAsStream("/vips-ipc-worker.jar")) {
      if (is == null) {
        throw new IOException(
            "Embedded worker JAR not found. " + "Please set jarPath() or rebuild the project.");
      }
      tmp = Files.createTempFile("vips-ipc-worker-", ".jar");
      tmp.toFile().deleteOnExit();
      Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
    cachedJarPath.compareAndSet(null, tmp);
    return cachedJarPath.get();
  }

  private List<String> buildCommand(String java, Path jar) {
    List<String> cmd = new ArrayList<>();
    cmd.add(java);
    cmd.addAll(jvmArgs);
    cmd.add("-jar");
    cmd.add(jar.toAbsolutePath().toString());
    return List.copyOf(cmd);
  }
}
