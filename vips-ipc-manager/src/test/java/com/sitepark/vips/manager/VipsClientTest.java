package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.CropStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import com.sitepark.vips.command.Thumbnail;
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

  // ── execute(Resize) ───────────────────────────────────────────

  @Test
  void testResizeDelegatesToWorkerProcess() throws IOException {
    this.client.execute(Resize.of(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5));
    verify(this.workerProcess).execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false));
  }

  @Test
  void testResizeThrowsIoExceptionFromWorkerProcess() throws IOException {
    doThrow(new IOException("worker error"))
        .when(this.workerProcess)
        .execute(new Resize("/src.jpg", "/dst.jpg", 0.5, false));
    assertThrows(
        IOException.class,
        () -> this.client.execute(Resize.of(Path.of("/src.jpg"), Path.of("/dst.jpg"), 0.5)),
        "execute(Resize) should propagate IOException from WorkerProcess");
  }

  // ── execute(Thumbnail) ────────────────────────────────────────

  @Test
  void testThumbnailDelegatesToWorkerProcess() throws IOException {
    this.client.execute(Thumbnail.of(Path.of("/src.jpg"), Path.of("/dst.jpg"), 800));
    verify(this.workerProcess).execute(new Thumbnail("/src.jpg", "/dst.jpg", 800, false));
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

  // ── execute(ScaleTransform) ───────────────────────────────────

  @Test
  void testScaleTransformDelegatesToWorkerProcess() throws IOException {
    ResizeStep resize = new ResizeStep(200, 100);
    BorderStep border = new BorderStep(5, 5);
    CropStep crop = new CropStep(190, 90, 5, 5);
    List<OutputFormat> formats = List.of(OutputFormat.jpeg());

    this.client.execute(
        ScaleTransform.of(
            Path.of("/src.jpg"), Path.of("/dst"), resize, border, crop, "FF0000", formats, null));

    verify(this.workerProcess)
        .execute(
            new ScaleTransform(
                "/src.jpg", "/dst", resize, border, crop, "FF0000", formats, null, false));
  }

  // ── execute(ScaleTransformBatch) ──────────────────────────────

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
                List.of(OutputFormat.jpeg()),
                null));

    this.client.execute(ScaleTransformBatch.of(Path.of("/src.jpg"), targets));

    verify(this.workerProcess).execute(new ScaleTransformBatch("/src.jpg", targets, false));
  }

  // ── close ─────────────────────────────────────────────────────

  @Test
  void testCloseForwardsToWorkerProcess() {
    this.client.close();
    verify(this.workerProcess).close();
  }
}
