package com.sitepark.vips.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sitepark.vips.handler.HandlerRegistry;
import com.sitepark.vips.response.OkResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerTest {

  private HandlerRegistry registry;
  private Worker worker;

  @BeforeEach
  void setUp() {
    this.registry = mock();
    this.worker = new Worker(this.registry);
  }

  private static InputStream toStream(String... lines) {
    String input = String.join("\n", lines) + "\n";
    return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
  }

  private static PrintStream toPrintStream(ByteArrayOutputStream buf) {
    return new PrintStream(buf, true, StandardCharsets.UTF_8);
  }

  // ── response serialization ────────────────────────────────────

  @Test
  void testProcessesCommandAndWritesJsonResponse() throws IOException {
    when(this.registry.dispatch(any())).thenReturn(new OkResponse(null, null));
    ByteArrayOutputStream buf = new ByteArrayOutputStream();

    this.worker.run(toStream("{\"command\":\"shutdown\"}"), toPrintStream(buf));

    assertEquals(
        "{\"status\":\"ok\"}",
        buf.toString(StandardCharsets.UTF_8).trim(),
        "Worker should write the serialized response to the output stream");
  }

  // ── shutdown ──────────────────────────────────────────────────

  @Test
  void testShutdownExitsLoopAfterResponse() throws IOException {
    when(this.registry.dispatch(any())).thenReturn(new OkResponse(null, null));
    // Two commands: shutdown + one more that must NOT be read
    ByteArrayOutputStream buf = new ByteArrayOutputStream();

    this.worker.run(
        toStream("{\"command\":\"shutdown\"}", "{\"command\":\"get-environment\"}"),
        toPrintStream(buf));

    assertEquals(
        1,
        buf.toString(StandardCharsets.UTF_8).trim().split("\n").length,
        "Worker should write exactly one response and stop after Shutdown");
  }

  // ── loop control ──────────────────────────────────────────────

  @Test
  void testNonShutdownCommandContinuesLoopUntilEof() throws IOException {
    when(this.registry.dispatch(any())).thenReturn(new OkResponse(null, null));
    ByteArrayOutputStream buf = new ByteArrayOutputStream();

    this.worker.run(toStream("{\"command\":\"get-environment\"}"), toPrintStream(buf));

    assertEquals(
        1,
        buf.toString(StandardCharsets.UTF_8).trim().split("\n").length,
        "Non-Shutdown command should be processed and run() should return cleanly on EOF");
  }

  // ── error handling ────────────────────────────────────────────

  @Test
  void testInvalidJsonThrowsException() {
    assertThrows(
        Exception.class,
        () ->
            this.worker.run(toStream("not-valid-json"), toPrintStream(new ByteArrayOutputStream())),
        "Worker.run() should propagate a parse exception for invalid JSON input");
  }
}
