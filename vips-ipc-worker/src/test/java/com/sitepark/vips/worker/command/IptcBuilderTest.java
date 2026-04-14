package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sitepark.vips.command.Metadata;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IptcBuilderTest {

  @Test
  void testBuildBlobWithAllNullFieldsReturnsEmptyArray() {
    byte[] result = IptcBuilder.buildBlob(new Metadata(null, null, null));
    assertEquals(0, result.length, "All-null metadata should produce an empty IPTC blob");
  }

  @Test
  void testBuildBlobEncodesTitle() {
    // tag 5 = ObjectName; "Test" = 4 ASCII bytes → header (5) + value (4) = 9 bytes
    byte[] expected = {0x1C, 0x02, 0x05, 0x00, 0x04, 'T', 'e', 's', 't'};
    byte[] result = IptcBuilder.buildBlob(new Metadata(null, "Test", null));
    assertArrayEquals(expected, result, "Title should be encoded at IPTC tag 5");
  }

  @Test
  void testBuildBlobEncodesCopyright() {
    // tag 116 (0x74) = CopyrightNotice; "(c)" = 3 ASCII bytes
    byte[] expected = {0x1C, 0x02, 0x74, 0x00, 0x03, '(', 'c', ')'};
    byte[] result = IptcBuilder.buildBlob(new Metadata("(c)", null, null));
    assertArrayEquals(expected, result, "Copyright should be encoded at IPTC tag 116");
  }

  @Test
  void testBuildBlobEncodesDescription() {
    // tag 120 (0x78) = Caption-Abstract; "Desc" = 4 ASCII bytes
    byte[] expected = {0x1C, 0x02, 0x78, 0x00, 0x04, 'D', 'e', 's', 'c'};
    byte[] result = IptcBuilder.buildBlob(new Metadata(null, null, "Desc"));
    assertArrayEquals(expected, result, "Description should be encoded at IPTC tag 120");
  }

  @Test
  void testBuildBlobFieldOrderIsTitleThenCopyrightThenDescription() {
    // buildBlob appends: title (tag 5), copyright (tag 116), description (tag 120)
    byte[] result = IptcBuilder.buildBlob(new Metadata("c", "t", "d"));
    assertEquals(0x05, result[2] & 0xFF, "First field in blob should be title (tag 5)");
  }

  @Test
  void testBuildBlobUtf8MultibyteCharactersExpandByteCount() {
    // "ä" encodes to 2 bytes in UTF-8; header is 5 bytes → total 7
    byte[] utf8 = "ä".getBytes(StandardCharsets.UTF_8);
    byte[] result = IptcBuilder.buildBlob(new Metadata(null, "ä", null));
    assertEquals(
        5 + utf8.length,
        result.length,
        "UTF-8 multibyte characters should expand the blob size correctly");
  }

  @Test
  void testBuildBlobThrowsWhenTitleExceedsMaxLength() {
    String longValue = "x".repeat(0x10000); // 65536 bytes > 65535-byte IPTC limit
    assertThrows(
        IllegalArgumentException.class,
        () -> IptcBuilder.buildBlob(new Metadata(null, longValue, null)),
        "Title exceeding 65535 bytes should throw IllegalArgumentException");
  }

  @Test
  void testBuildBlobThrowsWhenCopyrightExceedsMaxLength() {
    String longValue = "x".repeat(0x10000);
    assertThrows(
        IllegalArgumentException.class,
        () -> IptcBuilder.buildBlob(new Metadata(longValue, null, null)),
        "Copyright exceeding 65535 bytes should throw IllegalArgumentException");
  }

  @Test
  void testBuildBlobThrowsWhenDescriptionExceedsMaxLength() {
    String longValue = "x".repeat(0x10000);
    assertThrows(
        IllegalArgumentException.class,
        () -> IptcBuilder.buildBlob(new Metadata(null, null, longValue)),
        "Description exceeding 65535 bytes should throw IllegalArgumentException");
  }
}
