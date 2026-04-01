package com.sitepark.vips.manager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sitepark.vips.command.Resize;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkerProcessTest {

  private static final String TEST_CLASSPATH = System.getProperty("java.class.path");
  private static final String FAKE_WORKER_MAIN = "com.sitepark.vips.fakeworker.Main";
  private static final Resize RESIZE_CMD = new Resize("/src.jpg", "/dst.jpg", 0.5);

  private static WorkerProcess fakeWorker(String mode) throws IOException {
    return new WorkerProcessBuilder()
        .mainClass(FAKE_WORKER_MAIN)
        .workerClasspath(TEST_CLASSPATH)
        .workerArgs(List.of(mode))
        .build();
  }

  private static WorkerProcess fakeWorker(String mode, long timeoutMs) throws IOException {
    return new WorkerProcessBuilder()
        .mainClass(FAKE_WORKER_MAIN)
        .workerClasspath(TEST_CLASSPATH)
        .workerArgs(List.of(mode))
        .commandTimeoutMs(timeoutMs)
        .build();
  }

  // ── execute ───────────────────────────────────────────────────

  @Test
  void testExecuteDoesNotThrowOnOkResponse() throws IOException {
    try (WorkerProcess wp = fakeWorker("ok")) {
      assertDoesNotThrow(
          () -> wp.execute(RESIZE_CMD), "execute() should not throw when worker returns ok");
    }
  }

  @Test
  void testExecuteThrowsIoExceptionContainingWorkerErrorMessage() throws IOException {
    try (WorkerProcess wp = fakeWorker("error")) {
      assertThatThrownBy(() -> wp.execute(RESIZE_CMD))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Vips worker error");
    }
  }

  @Test
  void testExecuteThrowsIoExceptionOnUnexpectedResponseType() throws IOException {
    try (WorkerProcess wp = fakeWorker("unexpected")) {
      assertThrows(
          IOException.class,
          () -> wp.execute(RESIZE_CMD),
          "execute() should throw IOException when worker returns unexpected response type");
    }
  }

  // ── queryEnvironment ──────────────────────────────────────────

  @Test
  void testQueryEnvironmentReturnsVipsEnvironmentResponse() throws IOException {
    try (WorkerProcess wp = fakeWorker("unexpected")) {
      VipsEnvironmentResponse response = wp.queryEnvironment();
      assertEquals(
          new VipsEnvironmentResponse("fake-8.15.1", List.of("jpg", "png")),
          response,
          "queryEnvironment() should return the VipsEnvironmentResponse from the worker");
    }
  }

  @Test
  void testQueryEnvironmentThrowsIoExceptionOnErrorResponse() throws IOException {
    try (WorkerProcess wp = fakeWorker("error")) {
      assertThrows(
          IOException.class,
          () -> wp.queryEnvironment(),
          "queryEnvironment() should throw IOException when worker returns error response");
    }
  }

  // ── crash / retry ─────────────────────────────────────────────

  @Test
  void testWorkerCrashCausesIoException() throws IOException {
    try (WorkerProcess wp = fakeWorker("crash")) {
      assertThrows(
          IOException.class,
          () -> wp.execute(RESIZE_CMD),
          "execute() should throw IOException when worker process crashes immediately");
    }
  }

  @Test
  void testRetrySucceedsAfterWorkerCrashOnFirstResponse() throws IOException {
    try (WorkerProcess wp = fakeWorker("crash-after-first")) {
      wp.execute(RESIZE_CMD); // first command – ok, sets firstCommandHandled=true in fake worker
      assertDoesNotThrow(
          () -> wp.execute(RESIZE_CMD),
          "execute() should succeed after automatic retry with new worker process");
    }
  }

  // ── timeout ───────────────────────────────────────────────────

  @Test
  void testTimeoutCausesIoException() throws IOException {
    try (WorkerProcess wp = fakeWorker("timeout", 300)) {
      assertThrows(
          IOException.class,
          () -> wp.execute(RESIZE_CMD),
          "execute() should throw IOException when worker does not respond within the timeout");
    }
  }

  // ── close ─────────────────────────────────────────────────────

  @Test
  void testExecuteAfterCloseThrowsIllegalStateException() throws IOException {
    try (WorkerProcess wp = fakeWorker("ok")) {
      wp.close();
      assertThrows(
          IllegalStateException.class,
          () -> wp.execute(RESIZE_CMD),
          "execute() should throw IllegalStateException after close()");
    }
  }
}
