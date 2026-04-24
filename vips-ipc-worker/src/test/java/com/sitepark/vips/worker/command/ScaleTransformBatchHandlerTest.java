package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.*;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Metadata;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import com.sitepark.vips.worker.RequiresVips;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresVips
class ScaleTransformBatchHandlerTest {

  @TempDir Path tempDir;

  /**
   * Demonstrates the hscale/vscale rounding bug in ScaleTransformBatchHandler.
   *
   * <p>The portrait source image (402×600) is loaded once via {@code thumbnail()} with SIZE_FORCE
   * at maxWidth=300, maxHeight=200 (determined by the largest target). The second target (150×120)
   * then computes:
   *
   * <pre>
   *   hscale = 150 / 300 = 0.5
   *   vscale = 120 / 200 = 0.6   ← differs from hscale!
   * </pre>
   *
   * <p>libvips's {@code resize()} with a sequentially-loaded image rejects differing scale factors
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
                    List.of(OutputFormat.jpeg()),
                    null),
                new BatchTarget(
                    target2,
                    new ResizeStep(150, 120),
                    null,
                    null,
                    null,
                    List.of(OutputFormat.jpeg()),
                    null)),
            false);

    assertDoesNotThrow(
        () -> new ScaleTransformBatchHandler().handle(cmd),
        "Batch resize with mixed aspect ratio targets should not throw");
  }

  /**
   * Visual inspection test: produces a JPEG with a 30 px semi-transparent blue border (background
   * "0000FF80", alpha = 128). The border is composited against black before writing so the result
   * should show a dark-blue frame around the source image.
   *
   * <p>Output: {@code vips-ipc-worker/target/test-output/border_0000FF80.jpg}
   */
  @Test
  void testBorderWithSemiTransparentBlueOnBlackComposite() {
    String source = getTestResource("musterbild_hochkant_08.jpg");
    Path outputDir = Path.of("target/test-output").toAbsolutePath();
    String targetBase = outputDir.resolve("border_0000FF80").toString();
    Path output = Path.of(targetBase + ".jpg");

    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          ScaleTransformSupport.applyAndWrite(
              base,
              null,
              new BorderStep(30, 30),
              null,
              "0000FF80",
              targetBase,
              List.of(OutputFormat.jpeg()),
              null);
        });

    assertTrue(Files.exists(output), "Output JPEG should exist at " + output);
  }

  @Test
  void testBorderWithSemiTransparentBlueOnBlackCompositePng() throws IOException {
    String source = getTestResource("affe.png");
    Path outputDir = Path.of("target/test-output").toAbsolutePath();
    String targetBase = outputDir.resolve("affe_border_0000FF80").toString();
    Path output = Path.of(targetBase + ".png");

    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          ScaleTransformSupport.applyAndWrite(
              base,
              null,
              new BorderStep(30, 30),
              null,
              "0000FF80",
              targetBase,
              List.of(OutputFormat.png()),
              null);
        });

    BufferedImage img = ImageIO.read(output.toFile());
    int alpha = (img.getRGB(0, 0) >> 24) & 0xFF;
    assertEquals(
        0x80, alpha, "Border pixel at (0,0) should preserve alpha=128 (0x80) from background");
  }

  @Test
  void testBorderWithSemiTransparentBlueOnBlackCompositePngOpaqueSourcePixelIsFullyOpaque()
      throws IOException {
    String source = getTestResource("affe.png");
    Path outputDir = Path.of("target/test-output").toAbsolutePath();
    String targetBase = outputDir.resolve("affe_border_0000FF80").toString();
    Path output = Path.of(targetBase + ".png");

    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          ScaleTransformSupport.applyAndWrite(
              base,
              null,
              new BorderStep(30, 30),
              null,
              "0000FF80",
              targetBase,
              List.of(OutputFormat.png()),
              null);
        });

    BufferedImage img = ImageIO.read(output.toFile());
    int alpha = (img.getRGB(img.getWidth() / 2, img.getHeight() / 2) >> 24) & 0xFF;
    assertEquals(0xFF, alpha, "Opaque source pixel at image center should have alpha=255");
  }

  @Test
  void testScaleWithMetadataCopyrightInOutputJpeg() throws IOException {
    String source = getTestResource("generation_bruehl_stempel.jpg");
    Path output = tempDir.resolve("with_metadata.jpg");
    String targetBase = output.toString().replace(".jpg", "");
    var metadata = new Metadata("Test-Copyright-äöüß", null, null);

    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          ScaleTransformSupport.applyAndWrite(
              base,
              new ResizeStep(300, 200),
              null,
              null,
              null,
              targetBase,
              List.of(OutputFormat.jpeg()),
              metadata);
        });

    byte[] fileBytes = Files.readAllBytes(output);
    byte[] expectedBytes = "Test-Copyright-äöüß".getBytes(StandardCharsets.UTF_8);
    assertTrue(
        containsBytes(fileBytes, expectedBytes), "IPTC copyright should appear in output JPEG");
  }

  @Test
  void testScaleWithMetadataDescriptionInOutputJpeg() throws IOException {
    String source = getTestResource("generation_bruehl_stempel.jpg");
    Path output = tempDir.resolve("with_metadata_desc.jpg");
    String targetBase = output.toString().replace(".jpg", "");
    var metadata = new Metadata(null, null, "Test-Description-äöüß");

    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          ScaleTransformSupport.applyAndWrite(
              base,
              new ResizeStep(300, 200),
              null,
              null,
              null,
              targetBase,
              List.of(OutputFormat.jpeg()),
              metadata);
        });

    byte[] fileBytes = Files.readAllBytes(output);
    byte[] expectedBytes = "Test-Description-äöüß".getBytes(StandardCharsets.UTF_8);
    assertTrue(
        containsBytes(fileBytes, expectedBytes), "IPTC description should appear in output JPEG");
  }

  private static boolean containsBytes(byte[] haystack, byte[] needle) {
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      boolean matches = true;
      for (int j = 0; j < needle.length && matches; j++) {
        matches = haystack[i + j] == needle[j];
      }
      if (matches) {
        return true;
      }
    }
    return false;
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
