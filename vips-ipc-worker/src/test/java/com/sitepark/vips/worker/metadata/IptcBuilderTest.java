package com.sitepark.vips.worker.metadata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sitepark.vips.command.Metadata;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IptcBuilderTest {

  private static final byte[] CHARSET_DECLARATION = {
    0x1C, 0x01, 0x5A, 0x00, 0x03, 0x1B, 0x25, 0x47
  };

  /** The IIM stream with the leading 1:90 declaration removed. */
  private static byte[] datasetsOf(Metadata metadata) {
    byte[] iim = IptcBuilder.buildIim(metadata);
    return Arrays.copyOfRange(iim, CHARSET_DECLARATION.length, iim.length);
  }

  @Test
  void testBuildBlobWithNullMetadataReturnsEmptyArray() {
    assertEquals(
        0, IptcBuilder.buildBlob(null).length, "Null metadata should produce an empty IPTC blob");
  }

  @Test
  void testBuildBlobWithAllNullFieldsReturnsEmptyArray() {
    assertEquals(
        0,
        IptcBuilder.buildBlob(new Metadata(null, null, null)).length,
        "All-null metadata should produce an empty IPTC blob");
  }

  @Test
  void testBuildIimDeclaresUtf8AsFirstDataset() {
    byte[] result = IptcBuilder.buildIim(new Metadata(null, "Test", null));

    assertArrayEquals(
        CHARSET_DECLARATION,
        Arrays.copyOf(result, CHARSET_DECLARATION.length),
        "The stream should open with 1:90 = ESC % G so UTF-8 values are declared");
  }

  @Test
  void testBuildIimEncodesTitleAtDataset5() {
    byte[] expected = {0x1C, 0x02, 0x05, 0x00, 0x04, 'T', 'e', 's', 't'};

    assertArrayEquals(
        expected,
        datasetsOf(new Metadata(null, "Test", null)),
        "Title should be encoded at IPTC dataset 5");
  }

  @Test
  void testBuildIimEncodesCopyrightAtDataset116() {
    byte[] expected = {0x1C, 0x02, 0x74, 0x00, 0x03, '(', 'c', ')'};

    assertArrayEquals(
        expected,
        datasetsOf(new Metadata("(c)", null, null)),
        "Copyright should be encoded at IPTC dataset 116");
  }

  @Test
  void testBuildIimEncodesDescriptionAtDataset120() {
    byte[] expected = {0x1C, 0x02, 0x78, 0x00, 0x04, 'D', 'e', 's', 'c'};

    assertArrayEquals(
        expected,
        datasetsOf(new Metadata(null, null, "Desc")),
        "Description should be encoded at IPTC dataset 120");
  }

  @Test
  void testBuildIimEmitsDatasetsInAscendingOrder() {
    byte[] result = datasetsOf(new Metadata("c", "t", "d"));

    assertEquals(0x05, result[2] & 0xFF, "The first dataset emitted should be the title (5)");
  }

  @Test
  void testBuildIimUtf8MultibyteCharactersExpandByteCount() {
    byte[] utf8 = "ä".getBytes(StandardCharsets.UTF_8);

    assertEquals(
        5 + utf8.length,
        datasetsOf(new Metadata(null, "ä", null)).length,
        "UTF-8 multibyte characters should expand the blob size correctly");
  }

  @Test
  void testBuildBlobWrapsIimInPhotoshopImageResourceBlock() {
    byte[] result = IptcBuilder.buildBlob(new Metadata(null, "Test", null));

    assertEquals(
        "Photoshop 3.0\u00008BIM",
        new String(result, 0, 18, StandardCharsets.US_ASCII),
        "The blob must open with the Photoshop signature and an 8BIM resource block, since libvips"
            + " writes it into APP13 verbatim");
  }

  @Test
  void testBuildBlobRoundTripsThroughParser() {
    Metadata metadata = new Metadata("© 2026", "Titel äöüß", "Beschreibung");

    assertEquals(
        Map.of(
            5, List.of("Titel äöüß"),
            116, List.of("© 2026"),
            120, List.of("Beschreibung")),
        IptcParser.parse(IptcBuilder.buildBlob(metadata)),
        "What the builder writes a standard reader should read back unchanged");
  }

  @Test
  void testBuildIimThrowsWhenTitleExceedsMaxLength() {
    Metadata metadata = new Metadata(null, "x".repeat(0x10000), null);

    assertThrows(
        IllegalArgumentException.class,
        () -> IptcBuilder.buildIim(metadata),
        "A title exceeding 65535 bytes should throw IllegalArgumentException");
  }

  @Test
  void testBuildIimThrowsWhenCopyrightExceedsMaxLength() {
    Metadata metadata = new Metadata("x".repeat(0x10000), null, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> IptcBuilder.buildIim(metadata),
        "A copyright exceeding 65535 bytes should throw IllegalArgumentException");
  }

  @Test
  void testBuildIimThrowsWhenDescriptionExceedsMaxLength() {
    Metadata metadata = new Metadata(null, null, "x".repeat(0x10000));

    assertThrows(
        IllegalArgumentException.class,
        () -> IptcBuilder.buildIim(metadata),
        "A description exceeding 65535 bytes should throw IllegalArgumentException");
  }
}
