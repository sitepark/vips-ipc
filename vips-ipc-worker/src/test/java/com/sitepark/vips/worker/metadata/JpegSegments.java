package com.sitepark.vips.worker.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Minimal JPEG marker walker for tests.
 *
 * <p>Lets the metadata tests read the very bytes libvips would hand them — the {@code APP13} payload
 * is exactly what libvips exposes as {@code iptc-data}, and the {@code APP1} XMP payload (minus its
 * namespace header) is what it exposes as {@code xmp-data}. That keeps these tests free of libvips,
 * which is not installed everywhere.
 */
public final class JpegSegments {

  private static final int MARKER_PREFIX = 0xFF;
  private static final int MARKER_SOI = 0xD8;
  private static final int MARKER_EOI = 0xD9;
  private static final int MARKER_SOS = 0xDA;
  private static final int MARKER_APP1 = 0xE1;
  private static final int MARKER_APP13 = 0xED;

  private static final byte[] XMP_HEADER =
      "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);

  private JpegSegments() {}

  /** Returns the {@code APP13} payload — what libvips stores as {@code iptc-data}. */
  public static byte[] readIptc(Path jpeg) throws IOException {
    return findSegment(Files.readAllBytes(jpeg), MARKER_APP13, new byte[0]);
  }

  /** Returns the XMP packet from {@code APP1} — what libvips stores as {@code xmp-data}. */
  public static byte[] readXmp(Path jpeg) throws IOException {
    return findSegment(Files.readAllBytes(jpeg), MARKER_APP1, XMP_HEADER);
  }

  /**
   * Returns the payload of the first segment with the given marker whose payload starts with {@code
   * prefix}, with that prefix removed. Returns an empty array when no such segment exists.
   */
  private static byte[] findSegment(byte[] jpeg, int marker, byte[] prefix) {
    int offset = 2;
    while (offset + 4 <= jpeg.length) {
      if ((jpeg[offset] & 0xFF) != MARKER_PREFIX) {
        return new byte[0];
      }
      int current = jpeg[offset + 1] & 0xFF;
      if (current == MARKER_SOI || current == MARKER_EOI) {
        offset += 2;
        continue;
      }
      if (current == MARKER_SOS) {
        return new byte[0];
      }
      int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
      int from = offset + 4;
      int to = offset + 2 + length;
      if (to > jpeg.length) {
        return new byte[0];
      }
      if (current == marker && startsWith(jpeg, from, to, prefix)) {
        return Arrays.copyOfRange(jpeg, from + prefix.length, to);
      }
      offset = to;
    }
    return new byte[0];
  }

  private static boolean startsWith(byte[] data, int from, int to, byte[] prefix) {
    if (to - from < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (data[from + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
