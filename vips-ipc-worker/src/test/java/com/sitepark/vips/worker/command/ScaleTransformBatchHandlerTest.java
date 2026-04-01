package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.OutputFormatType;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import com.sitepark.vips.worker.WorkerConfig;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScaleTransformBatchHandlerTest {

  @TempDir Path tempDir;

  /**
   * Demonstrates the hscale/vscale rounding bug in ScaleTransformBatchHandler.
   *
   * <p>The portrait source image (402×600) is loaded once via {@code thumbnail()} with
   * SIZE_FORCE at maxWidth=300, maxHeight=200 (determined by the largest target). The second
   * target (150×120) then computes:
   *
   * <pre>
   *   hscale = 150 / 300 = 0.5
   *   vscale = 120 / 200 = 0.6   ← differs from hscale!
   * </pre>
   *
   * libvips's {@code resize()} with a sequentially-loaded image rejects differing scale factors
   * and throws an error, causing this test to fail.
   */
  @Test
  void testBatchResizeWithDifferentAspectRatioTargetsDoesNotThrow() {
    String source = getTestResource("musterbild_hochkant_08.jpg");
    String target1 = tempDir.resolve("output_300x200").toString();
    String target2 = tempDir.resolve("output_150x120").toString();

    var cmd =
        new ScaleTransformBatch(
            source,
            List.of(
                new BatchTarget(
                    target1,
                    new ResizeStep(300, 200),
                    null,
                    null,
                    null,
                    List.of(OutputFormat.of(OutputFormatType.JPG))),
                new BatchTarget(
                    target2,
                    new ResizeStep(150, 120),
                    null,
                    null,
                    null,
                    List.of(OutputFormat.of(OutputFormatType.JPG)))));

    assertDoesNotThrow(
        () -> new ScaleTransformBatchHandler(new WorkerConfig()).handle(cmd),
        "Batch resize with mixed aspect ratio targets should not throw");
  }

  @SuppressWarnings("PMD.LawOfDemeter")
  private String getTestResource(String name) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL url = cl.getResource(name);
    if (url == null) {
      throw new IllegalStateException("Test resource not found: " + name);
    }
    return new File(url.getFile()).getAbsolutePath();
  }
}
