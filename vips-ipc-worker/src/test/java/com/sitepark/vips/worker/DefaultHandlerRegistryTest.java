package com.sitepark.vips.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sitepark.vips.command.Config;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.Shutdown;
import com.sitepark.vips.command.Thumbnail;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import com.sitepark.vips.worker.command.ConfigHandler;
import com.sitepark.vips.worker.command.GetEnvironmentHandler;
import com.sitepark.vips.worker.command.ResizeHandler;
import com.sitepark.vips.worker.command.ScaleTransformBatchHandler;
import com.sitepark.vips.worker.command.ScaleTransformHandler;
import com.sitepark.vips.worker.command.ThumbnailHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultHandlerRegistryTest {

  private static final String TEST_WORKER_JAR_CMD = "java -jar worker.jar";

  private ConfigHandler configHandler;
  private GetEnvironmentHandler getEnvironmentHandler;
  private ResizeHandler resizeHandler;
  private ThumbnailHandler thumbnailHandler;
  private ScaleTransformHandler scaleTransformHandler;
  private ScaleTransformBatchHandler scaleTransformBatchHandler;
  private DefaultHandlerRegistry registry;

  @BeforeEach
  void setUp() {
    this.configHandler = mock();
    this.getEnvironmentHandler = mock();
    this.resizeHandler = mock();
    this.thumbnailHandler = mock();
    this.scaleTransformHandler = mock();
    this.scaleTransformBatchHandler = mock();
    this.registry =
        new DefaultHandlerRegistry(
            this.configHandler,
            this.getEnvironmentHandler,
            this.resizeHandler,
            this.thumbnailHandler,
            this.scaleTransformHandler,
            this.scaleTransformBatchHandler,
            TEST_WORKER_JAR_CMD);
  }

  // ── Config ────────────────────────────────────────────────────

  @Test
  void testDispatchConfigDelegatesToConfigHandler() {
    Config cmd = new Config(true, false);
    this.registry.dispatch(cmd);
    verify(this.configHandler).handle(cmd);
  }

  @Test
  void testDispatchConfigReturnsOkResponse() {
    assertEquals(
        new OkResponse(null),
        this.registry.dispatch(new Config(true, false)),
        "Config command should return OkResponse");
  }

  // ── Resize ────────────────────────────────────────────────────

  @Test
  void testDispatchResizeDelegatesToResizeHandler() {
    Resize cmd = new Resize("/src.jpg", "/dst.jpg", 0.5, false);
    this.registry.dispatch(cmd);
    verify(this.resizeHandler).handle(cmd);
  }

  @Test
  void testDispatchResizeReturnsOkResponse() {
    assertEquals(
        new OkResponse(null),
        this.registry.dispatch(new Resize("/src.jpg", "/dst.jpg", 0.5, false)),
        "Resize command should return OkResponse");
  }

  @Test
  void testDispatchResizeWithDebugReturnsNonNullDebugInfo() {
    OkResponse response =
        (OkResponse) this.registry.dispatch(new Resize("/src.jpg", "/dst.jpg", 0.5, true));
    assertNotNull(response.debug(), "Debug-enabled Resize command should include DebugInfo");
  }

  @Test
  void testDispatchResizeWithDebugCliCommandContainsWorkerJarCommand() {
    OkResponse response =
        (OkResponse) this.registry.dispatch(new Resize("/src.jpg", "/dst.jpg", 0.5, true));
    assertTrue(
        response.debug().cliCommand().contains(TEST_WORKER_JAR_CMD),
        "CLI command should contain the configured worker JAR command");
  }

  @Test
  void testDispatchResizeWithDebugCliCommandDoesNotContainDebugFlag() {
    OkResponse response =
        (OkResponse) this.registry.dispatch(new Resize("/src.jpg", "/dst.jpg", 0.5, true));
    assertFalse(
        response.debug().cliCommand().contains("\"debug\""),
        "CLI command JSON should not contain the debug flag");
  }

  // ── Thumbnail ─────────────────────────────────────────────────

  @Test
  void testDispatchThumbnailDelegatesToThumbnailHandler() {
    Thumbnail cmd = new Thumbnail("/src.jpg", "/dst.jpg", 800, false);
    this.registry.dispatch(cmd);
    verify(this.thumbnailHandler).handle(cmd);
  }

  @Test
  void testDispatchThumbnailReturnsOkResponse() {
    assertEquals(
        new OkResponse(null),
        this.registry.dispatch(new Thumbnail("/src.jpg", "/dst.jpg", 800, false)),
        "Thumbnail command should return OkResponse");
  }

  // ── ScaleTransform ────────────────────────────────────────────

  @Test
  void testDispatchScaleTransformDelegatesToScaleTransformHandler() {
    ScaleTransform cmd =
        new ScaleTransform(
            "/src.jpg", "/dst", new ResizeStep(200, 100), null, null, null, List.of(), false);
    this.registry.dispatch(cmd);
    verify(this.scaleTransformHandler).handle(cmd);
  }

  @Test
  void testDispatchScaleTransformReturnsOkResponse() {
    assertEquals(
        new OkResponse(null),
        this.registry.dispatch(
            new ScaleTransform(
                "/src.jpg", "/dst", new ResizeStep(200, 100), null, null, null, List.of(), false)),
        "ScaleTransform command should return OkResponse");
  }

  // ── ScaleTransformBatch ───────────────────────────────────────

  @Test
  void testDispatchScaleTransformBatchDelegatesToScaleTransformBatchHandler() {
    ScaleTransformBatch cmd = new ScaleTransformBatch("/src.jpg", List.of(), false);
    this.registry.dispatch(cmd);
    verify(this.scaleTransformBatchHandler).handle(cmd);
  }

  @Test
  void testDispatchScaleTransformBatchReturnsOkResponse() {
    assertEquals(
        new OkResponse(null),
        this.registry.dispatch(new ScaleTransformBatch("/src.jpg", List.of(), false)),
        "ScaleTransformBatch command should return OkResponse");
  }

  // ── GetEnvironment ────────────────────────────────────────────

  @Test
  void testDispatchGetEnvironmentDelegatesToGetEnvironmentHandler() {
    GetEnvironment cmd = new GetEnvironment();
    when(this.getEnvironmentHandler.handle(cmd))
        .thenReturn(new VipsEnvironmentResponse("8.15", List.of()));
    this.registry.dispatch(cmd);
    verify(this.getEnvironmentHandler).handle(cmd);
  }

  @Test
  void testDispatchGetEnvironmentReturnsEnvironmentResponse() {
    VipsEnvironmentResponse expected = new VipsEnvironmentResponse("8.15", List.of("jpg"));
    when(this.getEnvironmentHandler.handle(new GetEnvironment())).thenReturn(expected);
    assertEquals(
        expected,
        this.registry.dispatch(new GetEnvironment()),
        "GetEnvironment command should return the VipsEnvironmentResponse from the handler");
  }

  // ── Shutdown ──────────────────────────────────────────────────

  @Test
  void testDispatchShutdownReturnsOkResponse() {
    assertEquals(
        new OkResponse(null),
        this.registry.dispatch(new Shutdown()),
        "Shutdown command should return OkResponse without calling any handler");
  }

  // ── error handling ────────────────────────────────────────────

  @Test
  void testDispatchHandlerThrowingExceptionReturnsErrorResponse() {
    Resize cmd = new Resize("/src.jpg", "/dst.jpg", 0.5, false);
    org.mockito.Mockito.doThrow(new RuntimeException("vips error"))
        .when(this.resizeHandler)
        .handle(cmd);
    assertInstanceOf(
        ErrorResponse.class,
        this.registry.dispatch(cmd),
        "A handler exception should be caught and returned as ErrorResponse");
  }
}
