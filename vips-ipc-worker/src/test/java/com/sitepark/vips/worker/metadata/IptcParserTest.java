package com.sitepark.vips.worker.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Validates the test-only {@link IptcParser} against a real Photoshop-written file, so the
 * end-to-end assertions that rely on it mean something.
 */
class IptcParserTest {

  private static final int OBJECT_NAME = 5;
  private static final int CAPTION_ABSTRACT = 120;

  private static Path fixture(String name) throws URISyntaxException {
    return Path.of(IptcParserTest.class.getResource("/" + name).toURI());
  }

  @Test
  void testParseReadsPhotoshopWrittenIptc() throws IOException, URISyntaxException {
    Map<Integer, List<String>> fields =
        IptcParser.parse(JpegSegments.readIptc(fixture("generation_bruehl_stempel.jpg")));

    assertEquals(
        List.of("Dokumententitel äöüß"),
        fields.get(OBJECT_NAME),
        "ObjectName should be read from the 0x0404 resource of a real Photoshop file");
  }

  @Test
  void testParseHonoursCodedCharacterSetDeclaration() throws IOException, URISyntaxException {
    Map<Integer, List<String>> fields =
        IptcParser.parse(JpegSegments.readIptc(fixture("generation_bruehl_stempel.jpg")));

    assertEquals(
        List.of("Beschreibung äöüß"),
        fields.get(CAPTION_ABSTRACT),
        "UTF-8 declared via 1:90 should decode without replacement characters");
  }

  @Test
  void testParseDecodesLatin1WhenNoCharsetIsDeclared() {
    byte[] iim =
        dataset(
            IptcCharset.APPLICATION_RECORD,
            OBJECT_NAME,
            "Grün".getBytes(StandardCharsets.ISO_8859_1));

    assertEquals(
        List.of("Grün"),
        IptcParser.parse(PhotoshopIrb.wrapIptc(iim)).get(OBJECT_NAME),
        "Without a 1:90 declaration the IIM default of ISO-8859-1 applies");
  }

  @Test
  void testParseReadsExtendedLengthDataset() {
    // Values longer than 32767 bytes use the extended form: the high bit of the length field is
    // set and its low bits give the number of following bytes that hold the real length.
    String value = "x".repeat(40_000);
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    var iim = new ByteArrayOutputStream();
    iim.write(0x1C);
    iim.write(IptcCharset.APPLICATION_RECORD);
    iim.write(CAPTION_ABSTRACT);
    iim.write(0x80);
    iim.write(0x04); // extended: four length bytes follow
    iim.write((bytes.length >> 24) & 0xFF);
    iim.write((bytes.length >> 16) & 0xFF);
    iim.write((bytes.length >> 8) & 0xFF);
    iim.write(bytes.length & 0xFF);
    iim.writeBytes(bytes);

    assertEquals(
        List.of(value),
        IptcParser.parse(wrapUtf8(iim.toByteArray())).get(CAPTION_ABSTRACT),
        "The extended length form should be decoded");
  }

  @Test
  void testParseReturnsEmptyForNullBlob() {
    assertTrue(IptcParser.parse(null).isEmpty(), "A null blob should yield no fields");
  }

  @Test
  void testParseReturnsEmptyForBlobWithoutPhotoshopSignature() {
    byte[] bare = utf8Dataset(OBJECT_NAME, "Test");

    assertTrue(
        IptcParser.parse(bare).isEmpty(),
        "A bare IIM stream without the Photoshop wrapper is not valid iptc-data");
  }

  @Test
  void testParseReturnsEmptyForGarbage() {
    assertTrue(
        IptcParser.parse(new byte[] {1, 2, 3, 4, 5}).isEmpty(), "Garbage should yield no fields");
  }

  @Test
  void testParseDiscardsDatasetRunningPastEndOfBlob() {
    byte[] wrapped = wrapUtf8(utf8Dataset(OBJECT_NAME, "Titel"));
    byte[] truncated = Arrays.copyOf(wrapped, wrapped.length - 2);

    assertTrue(
        IptcParser.parse(truncated).isEmpty(),
        "A dataset whose declared length runs past the blob should be discarded, not guessed");
  }

  private static byte[] wrapUtf8(byte[] iim) {
    var declared = new ByteArrayOutputStream();
    declared.writeBytes(
        dataset(
            IptcCharset.ENVELOPE_RECORD,
            IptcCharset.CODED_CHARACTER_SET,
            IptcCharset.UTF8_DECLARATION));
    declared.writeBytes(iim);
    return PhotoshopIrb.wrapIptc(declared.toByteArray());
  }

  private static byte[] utf8Dataset(int datasetNumber, String value) {
    return dataset(
        IptcCharset.APPLICATION_RECORD, datasetNumber, value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] dataset(int record, int datasetNumber, byte[] value) {
    var out = new ByteArrayOutputStream();
    out.write(0x1C);
    out.write(record);
    out.write(datasetNumber);
    out.write((value.length >> 8) & 0xFF);
    out.write(value.length & 0xFF);
    out.writeBytes(value);
    return out.toByteArray();
  }
}
