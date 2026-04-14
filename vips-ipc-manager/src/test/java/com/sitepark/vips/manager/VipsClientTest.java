package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VipsClientTest {

  private WorkerBackend workerProcess;
  private VipsClient client;

  @BeforeEach
  void setUp() {
    this.workerProcess = mock();
    this.client = new VipsClient(this.workerProcess);
  }

  // ── resize ────────────────────────────────────────────────────

  @Test
  void testResizeDelegatesToWorkerProcess() throws IOException {
    this.client.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5);
    verify(this.workerProcess).execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false));
  }

  @Test
  void testResizeThrowsIoExceptionFromWorkerProcess() throws IOException {
    doThrow(new IOException("worker error"))
        .when(this.workerProcess)
        .execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false));
    assertThrows(
        IOException.class,
        () -> this.client.resize(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5),
        "resize() should propagate IOException from WorkerProcess");
  }

  // ── thumbnail ─────────────────────────────────────────────────

  @Test
  void testThumbnailDelegatesToWorkerProcess() throws IOException {
    this.client.thumbnail(Path.of("/src.jpg"), Path.of("/dst.jpg"), 800);
    verify(this.workerProcess).execute(new Thumbnail("/src.jpg", "/dst.jpg", 800, false));
  }

  // ── configure ─────────────────────────────────────────────────

  @Test
  void testConfigureDelegatesToWorkerProcess() throws IOException {
    this.client.configure(true, false);
    verify(this.workerProcess).execute(new Config(true, false));
  }

  // ── getEnvironment ────────────────────────────────────────────

  @Test
  void testGetEnvironmentDelegatesToWorkerProcess() throws IOException {
    VipsEnvironmentResponse expected = new VipsEnvironmentResponse("8.15.1", List.of("jpg", "png"));
    when(this.workerProcess.queryEnvironment()).thenReturn(expected);
    this.client.getEnvironment();
    verify(this.workerProcess).queryEnvironment();
  }

  @Test
  @SuppressWarnings("PMD.LawOfDemeter")
  void testGetEnvironmentThrowsIoExceptionFromWorkerProcess() throws IOException {
    when(this.workerProcess.queryEnvironment()).thenThrow(new IOException("worker error"));
    assertThrows(
        IOException.class,
        () -> this.client.getEnvironment(),
        "getEnvironment() should propagate IOException from WorkerProcess");
  }

  // ── scaleTransform ────────────────────────────────────────────

  @Test
  void testScaleTransformDelegatesToWorkerProcess() throws IOException {
    ResizeStep resize = new ResizeStep(200, 100);
    BorderStep border = new BorderStep(5, 5);
    CropStep crop = new CropStep(190, 90, 5, 5);
    List<com.sitepark.vips.command.OutputFormat> formats =
        List.of(
            com.sitepark.vips.command.OutputFormat.of(
                com.sitepark.vips.command.OutputFormatType.JPG));

    this.client.scaleTransform(
        Path.of("/src.jpg"), Path.of("/dst"), resize, border, crop, "FF0000", formats, null);

    verify(this.workerProcess)
        .execute(
            new ScaleTransform(
                "/src.jpg", "/dst", resize, border, crop, "FF0000", formats, null, false));
  }

  // ── scaleTransformBatch ───────────────────────────────────────

  @Test
  void testScaleTransformBatchDelegatesToWorkerProcess() throws IOException {
    List<BatchTarget> targets =
        List.of(
            new BatchTarget(
                "/out/large",
                new ResizeStep(800, 600),
                null,
                null,
                null,
                List.of(
                    com.sitepark.vips.command.OutputFormat.of(
                        com.sitepark.vips.command.OutputFormatType.JPG)),
                null));

    this.client.scaleTransformBatch(Path.of("/src.jpg"), targets);

    verify(this.workerProcess).execute(new ScaleTransformBatch("/src.jpg", targets, false));
  }

  // ── close ─────────────────────────────────────────────────────

  @Test
  void testCloseForwardsToWorkerProcess() {
    this.client.close();
    verify(this.workerProcess).close();
  }
}
