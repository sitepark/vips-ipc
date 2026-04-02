package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.sitepark.vips.command.Resize;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WorkerProcessBuilderTest {

  private static final String TEST_CLASSPATH = System.getProperty("java.class.path");
  private static final String FAKE_WORKER_MAIN = "com.sitepark.vips.fakeworker.Main";
  private static final Resize RESIZE_CMD = new Resize("/src.jpg", "/dst.jpg", 0.5, false);

  private static WorkerProcessBuilder fakeWorkerBuilder() {
    return new WorkerProcessBuilder()
        .mainClass(FAKE_WORKER_MAIN)
        .workerClasspath(TEST_CLASSPATH)
        .workerArgs(List.of("ok"));
  }

  @Test
  void testNiceLevelZeroDoesNotPrependNice() throws IOException {
    try (WorkerProcess wp = fakeWorkerBuilder().niceLevel(0).build()) {
      assertDoesNotThrow(
          () -> wp.execute(RESIZE_CMD),
          "Worker with niceLevel(0) should start without nice prefix and execute successfully");
    }
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testNiceLevelPositivePrependNice() throws IOException {
    try (WorkerProcess wp = fakeWorkerBuilder().niceLevel(10).build()) {
      assertDoesNotThrow(
          () -> wp.execute(RESIZE_CMD),
          "Worker with niceLevel(10) should start with 'nice -n 10' prefix and execute"
              + " successfully");
    }
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testNiceLevelNegativePrependNice() throws IOException {
    try (WorkerProcess wp = fakeWorkerBuilder().niceLevel(-5).build()) {
      assertDoesNotThrow(
          () -> wp.execute(RESIZE_CMD),
          "Worker with niceLevel(-5) should start with 'nice -n -5' prefix and execute"
              + " successfully");
    }
  }

  @Test
  void testCommandOverrideIsUnaffectedByNiceLevel() throws IOException {
    String javaExe =
        ProcessHandle.current()
            .info()
            .command()
            .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
    try (WorkerProcess wp =
        new WorkerProcessBuilder()
            .niceLevel(10)
            .commandOverride(
                List.of(
                    javaExe,
                    "--enable-native-access=ALL-UNNAMED",
                    "-Dvipsffm.autoinit=false",
                    "-Xms32m",
                    "-Xmx32m",
                    "-cp",
                    TEST_CLASSPATH,
                    FAKE_WORKER_MAIN,
                    "ok"))
            .build()) {
      assertDoesNotThrow(
          () -> wp.execute(RESIZE_CMD),
          "commandOverride() must bypass niceLevel and use the exact override command");
    }
  }
}
