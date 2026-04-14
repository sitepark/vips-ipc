package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.response.DebugInfo;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import com.sitepark.vips.worker.HandlerRegistry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InProcessWorkerBackendTest {

  private HandlerRegistry registry;
  private InProcessWorkerBackend backend;

  @BeforeEach
  void setUp() {
    this.registry = mock();
    this.backend = new InProcessWorkerBackend(() -> this.registry);
  }

  @Test
  void testExecuteDelegatesToRegistry() throws IOException {
    Resize cmd = new Resize("/src.jpg", "/dst.jpg", 0.5, false);
    when(this.registry.dispatch(cmd)).thenReturn(new OkResponse(null));
    this.backend.execute(cmd);
    verify(this.registry).dispatch(cmd);
  }

  @Test
  void testExecuteDoesNotThrowOnOkResponse() {
    when(this.registry.dispatch(any())).thenReturn(new OkResponse(null));
    assertDoesNotThrow(
        () -> this.backend.execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false)),
        "execute() should not throw when worker returns OkResponse");
  }

  @Test
  void testExecuteThrowsIoExceptionOnErrorResponse() {
    when(this.registry.dispatch(any()))
        .thenReturn(new ErrorResponse("worker error", "stack trace here"));
    assertThrows(
        IOException.class,
        () -> this.backend.execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false)),
        "execute() should throw IOException when worker returns ErrorResponse");
  }

  @Test
  void testExecuteThrowsIoExceptionOnUnexpectedResponseType() {
    when(this.registry.dispatch(any())).thenReturn(new VipsEnvironmentResponse("8.15", List.of()));
    assertThrows(
        IOException.class,
        () -> this.backend.execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false)),
        "execute() should throw IOException when worker returns an unexpected response type");
  }

  @Test
  void testQueryEnvironmentDelegatesToRegistry() throws IOException {
    VipsEnvironmentResponse expected = new VipsEnvironmentResponse("8.15.1", List.of("jpg"));
    when(this.registry.dispatch(any(GetEnvironment.class))).thenReturn(expected);
    VipsEnvironmentResponse result = this.backend.queryEnvironment();
    assertEquals(expected, result, "queryEnvironment() should return the VipsEnvironmentResponse");
  }

  @Test
  void testQueryEnvironmentThrowsIoExceptionOnErrorResponse() {
    when(this.registry.dispatch(any())).thenReturn(new ErrorResponse("fail", "stack"));
    assertThrows(
        IOException.class,
        () -> this.backend.queryEnvironment(),
        "queryEnvironment() should throw IOException when worker returns ErrorResponse");
  }

  @Test
  void testExecuteDoesNotThrowWhenDebugInfoIsPresent() {
    when(this.registry.dispatch(any()))
        .thenReturn(new OkResponse(new DebugInfo("echo '{}' | java -jar worker.jar")));
    assertDoesNotThrow(
        () -> this.backend.execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false)),
        "execute() should not throw when OkResponse includes non-null debug info");
  }

  @Test
  void testQueryEnvironmentThrowsIoExceptionOnUnexpectedResponseType() {
    when(this.registry.dispatch(any(GetEnvironment.class))).thenReturn(new OkResponse(null));
    assertThrows(
        IOException.class,
        () -> this.backend.queryEnvironment(),
        "queryEnvironment() should throw IOException when worker returns an unexpected response"
            + " type");
  }

  @Test
  void testCloseIsNoOp() {
    assertDoesNotThrow(() -> this.backend.close(), "close() should be a no-op");
  }

  @Test
  void testExecuteStillWorksAfterClose() {
    when(this.registry.dispatch(any())).thenReturn(new OkResponse(null));
    this.backend.close();
    assertDoesNotThrow(
        () -> this.backend.execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false)),
        "execute() should still work after close() since in-process backend has no closed state");
  }
}
