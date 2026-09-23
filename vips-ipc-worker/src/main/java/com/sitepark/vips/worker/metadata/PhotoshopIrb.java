package com.sitepark.vips.worker.metadata;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wraps an IPTC IIM stream in the Photoshop Image Resource Block (IRB) container that libvips
 * expects under the {@code iptc-data} field.
 *
 * <p>libvips takes the whole JPEG {@code APP13} payload as {@code iptc-data} on load and writes it
 * back verbatim on save, so the blob is not a bare IIM stream — it is:
 *
 * <pre>
 * "Photoshop 3.0\0"
 * ( "8BIM" &lt;id:2&gt; &lt;pascal name, padded to even&gt; &lt;size:4&gt; &lt;body&gt; &lt;pad to even&gt; )*
 * </pre>
 *
 * <p>The IPTC IIM stream lives in the resource with id {@code 0x0404}. Without this container a
 * reader sees an {@code APP13} segment with an unrecognised identifier and skips it, so the values
 * are present in the file yet invisible to every standard tool.
 */
final class PhotoshopIrb {

  /** Resource id of the IPTC-NAA (IIM) block. */
  static final int RESOURCE_IPTC = 0x0404;

  private static final byte[] SIGNATURE = "Photoshop 3.0\0".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] RESOURCE_TYPE = "8BIM".getBytes(StandardCharsets.US_ASCII);

  private PhotoshopIrb() {}

  /**
   * Wraps an IPTC IIM stream in a minimal IRB carrying only the {@code 0x0404} resource. Returns an
   * empty array for empty input so callers can skip setting the field entirely.
   */
  static byte[] wrapIptc(byte[] iim) {
    if (iim.length == 0) {
      return new byte[0];
    }
    var out = new ByteArrayOutputStream();
    out.writeBytes(SIGNATURE);
    out.writeBytes(RESOURCE_TYPE);
    out.write((RESOURCE_IPTC >> 8) & 0xFF);
    out.write(RESOURCE_IPTC & 0xFF);
    // Empty Pascal name: a single zero length byte plus one pad byte to reach an even width.
    out.write(0);
    out.write(0);
    out.write((iim.length >> 24) & 0xFF);
    out.write((iim.length >> 16) & 0xFF);
    out.write((iim.length >> 8) & 0xFF);
    out.write(iim.length & 0xFF);
    out.writeBytes(iim);
    if (iim.length % 2 != 0) {
      out.write(0);
    }
    return out.toByteArray();
  }
}
