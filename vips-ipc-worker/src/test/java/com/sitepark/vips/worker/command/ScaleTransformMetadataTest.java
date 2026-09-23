package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.enums.VipsAngle;
import com.sitepark.vips.command.Metadata;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.worker.RequiresVips;
import com.sitepark.vips.worker.metadata.IptcParser;
import com.sitepark.vips.worker.metadata.JpegSegments;
import com.sitepark.vips.worker.metadata.MetadataContext;
import com.sitepark.vips.worker.metadata.SourceMetadata;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * End-to-end metadata and orientation behaviour of the scale-transform write pipeline: what reaches
 * the output file, what must not, and that a rotated source is uprighted exactly once.
 */
@RequiresVips
class ScaleTransformMetadataTest {

  private static final int OBJECT_NAME = 5;
  private static final int COPYRIGHT_NOTICE = 116;
  private static final int CAPTION_ABSTRACT = 120;
  private static final String ORIENTATION_FIELD = "orientation";
  private static final int ROTATE_90_CW = 6;
  private static final String XMP_FIELD = "xmp-data";
  private static final String IPTC_EXT_NAMESPACE = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/";
  private static final String TRAINED_ALGORITHMIC =
      "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia";

  private final Path tempDir = Path.of("target/test-output");

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
   * No repo fixture carries DigitalSourceType, so the source is produced here from a literal XMP
   * packet. Writing the fixture by hand rather than with the production builder, and checking the
   * result by hand rather than with the production reader, keeps this test from confirming our own
   * writer with our own reader.
   */
  @Test
  void testDigitalSourceTypeIsCarriedIntoOutputJpeg() throws IOException {
    Path source = tempDir.resolve("ai_source.jpg");
    byte[] packet =
        ("<?xpacket begin=\"\ufeff\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description rdf:about=\"\" xmlns:iptcExt=\""
                + IPTC_EXT_NAMESPACE
                + "\"><iptcExt:DigitalSourceType>"
                + TRAINED_ALGORITHMIC
                + "</iptcExt:DigitalSourceType></rdf:Description></rdf:RDF></x:xmpmeta>"
                + "<?xpacket end=\"w\"?>")
            .getBytes(StandardCharsets.UTF_8);

    Vips.init();
    Vips.run(
        arena -> {
          VImage image =
              VImage.newFromFile(arena, getTestResource("musterbild_hochkant_08.jpg")).copy();
          // vips_image_set_blob_copy, not VImage.set(String, VBlob): the VBlob overload stores an
          // empty blob here, the same binding weakness that makes VImage.getBlob misreport sizes.
          VipsHelper.image_set_blob_copy(
              arena,
              image.getUnsafeStructAddress(),
              XMP_FIELD,
              arena.allocateFrom(ValueLayout.JAVA_BYTE, packet),
              packet.length);
          image.writeToFile(source.toString());
        });

    scale("ai_derived", new Metadata("(c)", "Titel", null), source.toString());
    String output =
        new String(JpegSegments.readXmp(tempDir.resolve("ai_derived.jpg")), StandardCharsets.UTF_8);

    assertTrue(
        output.contains(IPTC_EXT_NAMESPACE) && output.contains(TRAINED_ALGORITHMIC),
        "DigitalSourceType is the one field carried over from the source image");
  }

  /**
   * Writes a landscape-pixel source tagged "rotate 90° CW for display", i.e. an image whose display
   * geometry is portrait while its stored pixels are not. No repo fixture carries an orientation
   * other than 1, so the source is produced here.
   */
  private String writeRotatedSource(String name) {
    Path source = tempDir.resolve(name + ".jpg");
    Vips.init();
    Vips.run(
        arena -> {
          VImage landscape =
              VImage.newFromFile(arena, getTestResource("musterbild_hochkant_08.jpg"))
                  .rot(VipsAngle.ANGLE_D90)
                  .copy();
          landscape.set(ORIENTATION_FIELD, ROTATE_90_CW);
          landscape.writeToFile(source.toString());
        });
    return source.toString();
  }

  /** The stored orientation of {@code file}; an absent tag means upright. */
  private static int orientationOf(Path file) {
    int[] orientation = {1};
    Vips.run(
        arena -> {
          Integer value = VImage.newFromFile(arena, file.toString()).getInt(ORIENTATION_FIELD);
          orientation[0] = value == null ? 1 : value;
        });
    return orientation[0];
  }

  /** Runs the pipeline with no resize step, so the output keeps the source's own geometry. */
  private void scaleWithoutResize(String name, String source) {
    String targetBase = tempDir.resolve(name + ".jpg").toString().replace(".jpg", "");
    Vips.init();
    Vips.run(
        arena -> {
          VImage base = VImage.newFromFile(arena, source);
          var metadata = new MetadataContext(SourceMetadata.capture(base), null);
          ScaleTransformSupport.applyAndWrite(
              base.autorot(),
              null,
              null,
              null,
              null,
              targetBase,
              List.of(OutputFormat.jpeg()),
              metadata);
        });
  }

  /**
   * The source stores 600x402 landscape pixels tagged "rotate 90° for display", so its display
   * geometry is 402x600. With the rotation baked in the output is portrait; without autorot it would
   * come out 600x402 and every later crop or border offset would land on swapped axes.
   */
  @Test
  void testRotatedSourceIsUprightedBeforeProcessing() throws IOException {
    scaleWithoutResize("rotated_geometry", writeRotatedSource("rotated_src_geometry"));

    BufferedImage output = ImageIO.read(tempDir.resolve("rotated_geometry.jpg").toFile());

    assertEquals(
        List.of(402, 600),
        List.of(output.getWidth(), output.getHeight()),
        "The rotation must be applied to the pixels, giving the source's display geometry");
  }

  @Test
  void testRotatedSourceIsNotRotatedTwice() throws IOException {
    scaleWithoutResize("rotated_upright", writeRotatedSource("rotated_src_upright"));

    assertEquals(
        1,
        orientationOf(tempDir.resolve("rotated_upright.jpg")),
        "autorot bakes the rotation into the pixels, so a leftover tag would rotate it again");
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

  private String getTestResource(String name) {
    return Fixtures.path(name);
  }
}
