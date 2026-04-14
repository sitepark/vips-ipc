package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sitepark.vips.command.*;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.CropStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VipsClientPoolTest {

  private WorkerBackend worker;
  private VipsClientPool pool;

  @BeforeEach
  void setUp() {
    this.worker = mock();
    this.pool = new VipsClientPool(List.of(this.worker));
  }

  // ── resize ────────────────────────────────────────────────────

  @Test
  void testResizeDelegatesToWorker() throws IOException {
    this.pool.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5);
    verify(this.worker).execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false));
  }

  @Test
  void testResizeWithDebugDelegatesToWorker() throws IOException {
    this.pool.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5, true);
    verify(this.worker).execute(new Resize("/src.jpg", "/dst.jpg", 0.5, true));
  }

  // ── thumbnail ─────────────────────────────────────────────────

  @Test
  void testThumbnailDelegatesToWorker() throws IOException {
    this.pool.thumbnail(Path.of("/src.jpg"), Path.of("/dst.jpg"), 800);
    verify(this.worker).execute(new Thumbnail("/src.jpg", "/dst.jpg", 800, false));
  }

  // ── configure ─────────────────────────────────────────────────

  @Test
  void testConfigureDelegatesToWorker() throws IOException {
    this.pool.configure(true, false);
    verify(this.worker).execute(new Config(true, false));
  }

  // ── configureAll ──────────────────────────────────────────────

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void testConfigureAllConfiguresAllWorkers() throws IOException {
    WorkerBackend worker1 = mock();
    WorkerBackend worker2 = mock();
    AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(
            inv -> {
              callCount.incrementAndGet();
              return null;
            })
        .when(worker1)
        .execute(any());
    doAnswer(
            inv -> {
              callCount.incrementAndGet();
              return null;
            })
        .when(worker2)
        .execute(any());

    new VipsClientPool(List.of(worker1, worker2)).configureAll(true, false);

    assertEquals(2, callCount.get(), "configureAll() should configure each worker exactly once");
  }

  // ── getEnvironment ────────────────────────────────────────────

  @Test
  void testGetEnvironmentDelegatesToWorker() throws IOException {
    VipsEnvironmentResponse expected = new VipsEnvironmentResponse("8.15.1", List.of("jpg", "png"));
    when(this.worker.queryEnvironment()).thenReturn(expected);
    this.pool.getEnvironment();
    verify(this.worker).queryEnvironment();
  }

  // ── scaleTransform ────────────────────────────────────────────

  @Test
  void testScaleTransformDelegatesToWorker() throws IOException {
    ResizeStep resize = new ResizeStep(200, 100);
    BorderStep border = new BorderStep(5, 5);
    CropStep crop = new CropStep(190, 90, 5, 5);
    List<OutputFormat> formats = List.of(OutputFormat.of(OutputFormatType.JPG));

    this.pool.scaleTransform(
        Path.of("/src.jpg"), Path.of("/dst"), resize, border, crop, "FF0000", formats, null);

    verify(this.worker)
        .execute(
            new ScaleTransform(
                "/src.jpg", "/dst", resize, border, crop, "FF0000", formats, null, false));
  }

  // ── scaleTransformBatch ───────────────────────────────────────

  @Test
  void testScaleTransformBatchDelegatesToWorker() throws IOException {
    List<BatchTarget> targets =
        List.of(
            new BatchTarget(
                "/out/large",
                new ResizeStep(800, 600),
                null,
                null,
                null,
                List.of(OutputFormat.of(OutputFormatType.JPG)),
                null));

    this.pool.scaleTransformBatch(Path.of("/src.jpg"), targets);

    verify(this.worker).execute(new ScaleTransformBatch("/src.jpg", targets, false));
  }

  // ── worker lifecycle ──────────────────────────────────────────

  @Test
  void testWorkerIsReturnedAfterSuccessfulCall() throws IOException {
    this.pool.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5);
    assertDoesNotThrow(
        () -> this.pool.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5),
        "Worker should be returned to pool after a successful call, allowing the next call");
  }

  @Test
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  void testWorkerIsReturnedAfterExceptionInWorker() throws IOException {
    doThrow(new IOException("worker error")).doNothing().when(this.worker).execute(any());
    try {
      this.pool.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5);
    } catch (Exception ignored) {
      // expected on first call
    }
    assertDoesNotThrow(
        () -> this.pool.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5),
        "Worker should be returned to pool even after an exception, allowing the next call");
  }

  // ── close ─────────────────────────────────────────────────────

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void testCloseCallsCloseOnFirstWorker() {
    WorkerBackend worker1 = mock();
    WorkerBackend worker2 = mock();
    new VipsClientPool(List.of(worker1, worker2)).close();
    verify(worker1).close();
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void testCloseCallsCloseOnSecondWorker() {
    WorkerBackend worker1 = mock();
    WorkerBackend worker2 = mock();
    new VipsClientPool(List.of(worker1, worker2)).close();
    verify(worker2).close();
  }

  // ── buildPool validation ──────────────────────────────────────

  @Test
  void testBuildPoolThrowsOnPoolSizeZero() {
    assertThrows(
        IllegalArgumentException.class,
        () -> VipsClient.builder().buildPool(0),
        "buildPool(0) should throw IllegalArgumentException");
  }

  @Test
  void testBuildPoolThrowsOnNegativePoolSize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> VipsClient.builder().buildPool(-1),
        "buildPool(-1) should throw IllegalArgumentException");
  }
}
