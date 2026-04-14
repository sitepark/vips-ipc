package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VipsHelper;
import com.sitepark.vips.command.Metadata;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * Builds and sets IPTC IIM Application2 metadata on a VImage.
 *
 * <p>Each field is encoded as: {@code 0x1C 0x02 <tag> <len_hi> <len_lo> <utf8_value>}.
 *
 * <ul>
 *   <li>Tag 5 — ObjectName (title)
 *   <li>Tag 116 — CopyrightNotice
 *   <li>Tag 120 — Caption-Abstract (description)
 * </ul>
 */
final class IptcBuilder {

  private static final int IPTC_MAX_FIELD_BYTES = 0xFFFF;

  private IptcBuilder() {}

  /**
   * Sets the IPTC {@code iptc-data} blob on {@code image} for the non-null fields of
   * {@code metadata}. Returns {@code image} for fluent chaining. Uses
   * {@code vips_image_set_blob_copy} so libvips immediately owns a copy of the blob; the temp
   * arena is closed right after the call.
   */
  static VImage applyToImage(VImage image, Metadata metadata) {
    if (metadata == null) {
      return image;
    }
    byte[] iptcData = buildBlob(metadata);
    if (iptcData.length == 0) {
      return image;
    }
    try (Arena arena = Arena.ofConfined()) {
      var segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, iptcData);
      VipsHelper.image_set_blob_copy(
          arena, image.getUnsafeStructAddress(), "iptc-data", segment, iptcData.length);
    }
    return image;
  }

  static byte[] buildBlob(Metadata metadata) {
    var out = new ByteArrayOutputStream();
    appendField(out, 5, metadata.title());
    appendField(out, 116, metadata.copyright());
    appendField(out, 120, metadata.description());
    return out.toByteArray();
  }

  private static void appendField(ByteArrayOutputStream out, int tag, String value) {
    if (value == null) {
      return;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > IPTC_MAX_FIELD_BYTES) {
      throw new IllegalArgumentException(
          "IPTC field value exceeds maximum length of 65535 bytes (tag " + tag + ")");
    }
    out.write(0x1C);
    out.write(0x02);
    out.write(tag);
    out.write((bytes.length >> 8) & 0xFF);
    out.write(bytes.length & 0xFF);
    out.write(bytes, 0, bytes.length);
  }
}
