package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.*;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.enums.VipsInterpretation;
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
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresVips
class ScaleTransformBatchHandlerTest {

  Path tempDir = Path.of("target/test-output");

  private static final int OBJECT_NAME = 5;
  private static final int COPYRIGHT_NOTICE = 116;
  private static final int CAPTION_ABSTRACT = 120;
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
   * Runs the write pipeline over the metadata-rich fixture and parses the IPTC block back out of the
   * result.
   *
   * <p>Parsing rather than scanning for raw bytes is deliberate: a byte scan passes even when the
   * IPTC stream is written without its Photoshop wrapper, which is how the missing wrapper went
   * unnoticed.
   */
  private Map<Integer, List<String>> scaleAndReadIptc(String name, Metadata explicit)
      throws IOException {
    return scaleAndReadIptc(name, explicit, getTestResource("generation_bruehl_stempel.jpg"));
  }

  private Map<Integer, List<String>> scaleAndReadIptc(String name, Metadata explicit, String source)
      throws IOException {
    scale(name, explicit, source);
    return IptcParser.parse(JpegSegments.readIptc(tempDir.resolve(name + ".jpg")));
  }

  private void scale(String name, Metadata explicit, String source) {
    String targetBase = tempDir.resolve(name + ".jpg").toString().replace(".jpg", "");
    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          var metadata = new MetadataContext(SourceMetadata.capture(base), explicit);
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
  }

  @Test
  void testExplicitMetadataIsWrittenToOutputJpeg() throws IOException {
    assertEquals(
        Map.of(
            OBJECT_NAME, List.of("Test-Titel-äöüß"),
            COPYRIGHT_NOTICE, List.of("Test-Copyright-äöüß"),
            CAPTION_ABSTRACT, List.of("Test-Description-äöüß")),
        scaleAndReadIptc(
            "with_metadata",
            new Metadata("Test-Copyright-äöüß", "Test-Titel-äöüß", "Test-Description-äöüß")),
        "The caller's metadata should land in a readable IPTC block");
  }

  @Test
  void testOnlyTheFieldsTheCallerSuppliedAreWritten() throws IOException {
    assertEquals(
        Map.of(COPYRIGHT_NOTICE, List.of("Nur-Copyright")),
        scaleAndReadIptc("partial_metadata", new Metadata("Nur-Copyright", null, null)),
        "Fields the caller left null must stay absent rather than fall back to the source");
  }

  @Test
  void testSourceIptcIsNotCarriedIntoOutputJpeg() throws IOException {
    assertEquals(
        Map.of(),
        scaleAndReadIptc("no_metadata", null),
        "Source IPTC is not copied, so without a Metadata the output carries none at all");
  }

  @Test
  void testSourceIptcValuesDoNotLeakIntoOutputJpeg() throws IOException {
    scale("no_leak", null, getTestResource("generation_bruehl_stempel.jpg"));
    byte[] fileBytes = Files.readAllBytes(tempDir.resolve("no_leak.jpg"));

    List<String> leaked =
        Stream.of(
                "Dokumententitel",
                "IPTC Ersteller",
                "Stichwörter",
                "Berufstitel",
                "Verfasser",
                "www.sitepark.com",
                "Adobe Photoshop CS5")
            .filter(v -> containsBytes(fileBytes, v.getBytes(StandardCharsets.UTF_8)))
            .toList();

    assertEquals(List.of(), leaked, "No source IPTC, Photoshop or EXIF value may reach the output");
  }

  @Test
  void testEmbeddedExifThumbnailIsDroppedFromOutputJpeg() throws IOException {
    // The source carries a 1695-byte EXIF thumbnail. libvips exposes it as its own field and would
    // write it back as IFD1, shipping a small copy of the original inside every derivative.
    scale("no_thumbnail", null, getTestResource("generation_bruehl_stempel.jpg"));

    byte[] sourceBytes =
        Files.readAllBytes(Path.of(getTestResource("generation_bruehl_stempel.jpg")));
    byte[] outputBytes = Files.readAllBytes(tempDir.resolve("no_thumbnail.jpg"));
    // A distinctive run from inside the source's embedded thumbnail.
    byte[] thumbnailMarker = new byte[64];
    System.arraycopy(sourceBytes, 426 + 200, thumbnailMarker, 0, thumbnailMarker.length);

    assertFalse(
        containsBytes(outputBytes, thumbnailMarker),
        "The embedded EXIF thumbnail must not reach the output");
  }

  /**
   * No repo fixture carries DigitalSourceType, so the source is produced here: set the XMP packet on
   * an image and save it. Generating it with libvips keeps the test free of external tooling.
   */
  @Test
  void testDigitalSourceTypeIsCarriedIntoOutputJpeg() throws IOException {
    Path source = tempDir.resolve("ai_source.jpg");
    byte[] packet =
        XmpBuilder.buildPacket(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of(TRAINED_ALGORITHMIC)));

    Vips.init();
    Vips.run(
        arena -> {
          VImage image =
              VImage.newFromFile(arena, getTestResource("musterbild_hochkant_08.jpg")).copy();
          ImageMetadata.setBlob(image, ImageMetadata.XMP, packet);
          image.writeToFile(source.toString());
        });

    scale("ai_derived", new Metadata("(c)", "Titel", null), source.toString());
    byte[] xmpData = JpegSegments.readXmp(tempDir.resolve("ai_derived.jpg"));

    assertEquals(
        List.of(TRAINED_ALGORITHMIC),
        XmpReader.parse(xmpData).get(XmpTag.DIGITAL_SOURCE_TYPE),
        "DigitalSourceType is the one field carried over from the source image");
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
