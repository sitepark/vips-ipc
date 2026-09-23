package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.*;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.enums.VipsInterpretation;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import com.sitepark.vips.worker.RequiresVips;
import com.sitepark.vips.worker.metadata.MetadataContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresVips
class ScaleTransformBatchHandlerTest {

  Path tempDir = Path.of("target/test-output");

  private static final int OBJECT_NAME = 5;
  private static final int COPYRIGHT_NOTICE = 116;
  private static final int CAPTION_ABSTRACT = 120;
  private static final String ORIENTATION_FIELD = "orientation";
  private static final int ROTATE_90_CW = 6;
  private static final String XMP_FIELD = "xmp-data";
  private static final String IPTC_EXT_NAMESPACE = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/";
  private static final String TRAINED_ALGORITHMIC =
      "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia";

  /**
   * A minimal grayscale SVG used as an own test source (not the customer file, never committed —
   * written to a {@link TempDir} at runtime). Only black shapes, no color, so an old libvips would
   * render it as a low-band-count image; the test forces that situation deterministically below.
   */
  private static final String GRAY_SVG =
      """
      <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
        <rect x="10" y="10" width="80" height="80" fill="#333333" stroke="#000000"/>
      </svg>
      """;

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
                    List.of(OutputFormat.jpeg().withAppendExtension(false)),
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
              MetadataContext.empty());
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
              MetadataContext.empty());
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
              MetadataContext.empty());
        });

    BufferedImage img = ImageIO.read(output.toFile());
    int alpha = (img.getRGB(img.getWidth() / 2, img.getHeight() / 2) >> 24) & 0xFF;
    assertEquals(0xFF, alpha, "Opaque source pixel at image center should have alpha=255");
  }

  /**
   * Regression test for the VipsError "linear: vector must have 1 or 2 elements".
   *
   * <p>On the customer server an SVG arrives as a 2-band image (gray + alpha) instead of RGBA. The
   * write path then applied a fixed 4-element background vector via {@code linear}, which libvips
   * rejects for a 2-band image. Modern libvips loads even a grayscale SVG as 4-band RGBA, so we
   * reproduce the 2-band situation deterministically with {@code colourspace(B_W)} — this keeps the
   * test meaningful on any libvips version. {@code applyAndWrite} must not throw because it now
   * normalises to sRGB first.
   */
  @Test
  void testTwoBandGrayscaleSvgSourceDoesNotThrow(@TempDir Path tmp) throws IOException {
    Path svg = tmp.resolve("gray.svg");
    Files.writeString(svg, GRAY_SVG);
    String source = svg.toAbsolutePath().toString();

    Path outputDir = Path.of("target/test-output").toAbsolutePath();
    String targetBase = outputDir.resolve("gray_svg_two_band").toString();

    Vips.init();
    assertDoesNotThrow(
        () ->
            Vips.run(
                arena -> {
                  // Force a 2-band (gray + alpha) image, mirroring the customer server's SVG load.
                  VImage base =
                      VImage.newFromFile(arena, source)
                          .colourspace(VipsInterpretation.INTERPRETATION_B_W);
                  ScaleTransformSupport.applyAndWrite(
                      base,
                      null,
                      null,
                      null,
                      "0000FF80",
                      targetBase,
                      // png exercises the linear composite path (the actual crash),
                      // jpeg the flatten path.
                      List.of(OutputFormat.png(), OutputFormat.jpeg()),
                      MetadataContext.empty());
                }),
        "2-band grayscale SVG source must not crash on linear/flatten");
  }

  private String getTestResource(String name) {
    return Fixtures.path(name);
  }
}
