package com.sitepark.vips.worker.command;

import com.sitepark.vips.command.Metadata;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds the libvips {@code iptc-data} blob from a caller-supplied {@link Metadata}.
 *
 * <p>The result is a complete {@link PhotoshopIrb} — {@code "Photoshop 3.0\0"} followed by an {@code
 * 8BIM} resource {@code 0x0404} holding the IPTC IIM stream — because libvips writes this blob into
 * JPEG {@code APP13} verbatim, and readers only recognise IPTC inside that container.
 *
 * <p>Each dataset is encoded as {@code 0x1C <record> <dataset> <len_hi> <len_lo> <utf8_bytes>}, and
 * the stream opens with {@code 1:90 = ESC % G} so the UTF-8 values are correctly declared.
 *
 * <ul>
 *   <li>Dataset 5 — ObjectName (title)
 *   <li>Dataset 116 — CopyrightNotice
 *   <li>Dataset 120 — Caption-Abstract (description)
 * </ul>
 */
final class IptcBuilder {

  private static final int TAG_MARKER = 0x1C;
  private static final int IPTC_MAX_FIELD_BYTES = 0xFFFF;

  private static final int OBJECT_NAME = 5;
  private static final int COPYRIGHT_NOTICE = 116;
  private static final int CAPTION_ABSTRACT = 120;

  private IptcBuilder() {}

  /**
   * Builds the {@code iptc-data} blob. Returns an empty array when {@code metadata} is {@code null}
   * or carries no value, so callers can skip setting the field rather than emit an empty IPTC block.
   *
   * @throws IllegalArgumentException if a value exceeds {@value #IPTC_MAX_FIELD_BYTES} UTF-8 bytes
   */
  static byte[] buildBlob(Metadata metadata) {
    return PhotoshopIrb.wrapIptc(buildIim(metadata));
  }

  /** Builds the bare IIM dataset stream. Package-private so tests can assert it without the IRB. */
  static byte[] buildIim(Metadata metadata) {
    if (metadata == null) {
      return new byte[0];
    }
    var out = new ByteArrayOutputStream();
    // Emitted in ascending dataset order.
    appendField(out, OBJECT_NAME, metadata.title());
    appendField(out, COPYRIGHT_NOTICE, metadata.copyright());
    appendField(out, CAPTION_ABSTRACT, metadata.description());
    if (out.size() == 0) {
      return new byte[0];
    }

    var declared = new ByteArrayOutputStream();
    appendDataset(
        declared,
        IptcCharset.ENVELOPE_RECORD,
        IptcCharset.CODED_CHARACTER_SET,
        IptcCharset.UTF8_DECLARATION);
    declared.writeBytes(out.toByteArray());
    return declared.toByteArray();
  }

  private static void appendField(ByteArrayOutputStream out, int dataset, String value) {
    if (value == null) {
      return;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > IPTC_MAX_FIELD_BYTES) {
      throw new IllegalArgumentException(
          "IPTC field value exceeds maximum length of "
              + IPTC_MAX_FIELD_BYTES
              + " bytes (dataset "
              + dataset
              + ")");
    }
    appendDataset(out, IptcCharset.APPLICATION_RECORD, dataset, bytes);
  }

  private static void appendDataset(
      ByteArrayOutputStream out, int record, int dataset, byte[] value) {
    out.write(TAG_MARKER);
    out.write(record);
    out.write(dataset);
    out.write((value.length >> 8) & 0xFF);
    out.write(value.length & 0xFF);
    out.writeBytes(value);
  }
}
