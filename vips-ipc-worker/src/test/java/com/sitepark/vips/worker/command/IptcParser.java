package com.sitepark.vips.worker.command;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only reader for the libvips {@code iptc-data} blob.
 *
 * <p>Production code only ever writes IPTC, so this lives in test scope: it exists to assert what
 * the pipeline actually produced, by parsing it the way a real reader would rather than scanning the
 * output for raw value bytes. A byte scan passes even when the IIM stream is written without its
 * Photoshop wrapper, which is exactly how that defect went unnoticed.
 *
 * <p>The blob is a Photoshop Image Resource Block:
 *
 * <pre>
 * "Photoshop 3.0\0"
 * ( "8BIM" &lt;id:2&gt; &lt;pascal name, padded to even&gt; &lt;size:4&gt; &lt;body&gt; &lt;pad to even&gt; )*
 * </pre>
 *
 * <p>Resource {@code 0x0404} holds the IIM datasets:
 *
 * <pre>
 * 0x1C &lt;record:1&gt; &lt;dataset:1&gt; &lt;len:2&gt; &lt;value&gt;
 * </pre>
 *
 * <p>When the high bit of {@code len} is set the low 15 bits give the number of following bytes that
 * hold the real length.
 */
// Test-scope byte parser: the maps are method-local and single-threaded, and decoding a byte
// stream means allocating per dataset.
@SuppressWarnings({
  "PMD.TooManyMethods",
  "PMD.UseConcurrentHashMap",
  "PMD.AvoidInstantiatingObjectsInLoops"
})
final class IptcParser {

  static final int RESOURCE_IPTC = 0x0404;

  private static final byte[] SIGNATURE = "Photoshop 3.0\0".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] RESOURCE_TYPE = "8BIM".getBytes(StandardCharsets.US_ASCII);
  private static final int TAG_MARKER = 0x1C;
  private static final int EXTENDED_LENGTH_FLAG = 0x8000;
  private static final int MAX_EXTENDED_LENGTH_BYTES = 4;
  private static final int DATASET_HEADER_LENGTH = 5;

  private IptcParser() {}

  /**
   * Parses {@code iptcData} into Application2 dataset number → values, in the order encountered.
   * Damaged input yields whatever could be read before the damage. Never returns {@code null}.
   */
  static Map<Integer, List<String>> parse(byte[] iptcData) {
    if (iptcData == null) {
      return new LinkedHashMap<>();
    }
    return decode(scan(extractResource(iptcData, RESOURCE_IPTC)));
  }

  /** Returns the body of resource {@code resourceId}, or an empty array when it is not present. */
  static byte[] extractResource(byte[] irb, int resourceId) {
    if (irb == null || !startsWith(irb, 0, SIGNATURE)) {
      return new byte[0];
    }
    int offset = SIGNATURE.length;
    while (offset + RESOURCE_TYPE.length + 2 <= irb.length
        && startsWith(irb, offset, RESOURCE_TYPE)) {
      int id = readUnsignedShort(irb, offset + RESOURCE_TYPE.length);
      int bodyStart = bodyStart(irb, offset);
      if (bodyStart < 0) {
        return new byte[0];
      }
      int size = readInt(irb, bodyStart - 4);
      if (size < 0 || bodyStart + size > irb.length) {
        return new byte[0];
      }
      if (id == resourceId) {
        return Arrays.copyOfRange(irb, bodyStart, bodyStart + size);
      }
      offset = bodyStart + size + (size % 2);
    }
    return new byte[0];
  }

  private static int bodyStart(byte[] irb, int offset) {
    int cursor = offset + RESOURCE_TYPE.length + 2;
    if (cursor >= irb.length) {
      return -1;
    }
    int nameLength = irb[cursor] & 0xFF;
    cursor += 1 + nameLength;
    cursor += (1 + nameLength) % 2;
    return cursor + 4 > irb.length ? -1 : cursor + 4;
  }

  /**
   * Collects raw Application2 values plus the character-set declaration. Values stay as bytes
   * because dataset 1:90 — which decides the encoding — need not precede the datasets it governs.
   */
  private static RawFields scan(byte[] iim) {
    Map<Integer, List<byte[]>> values = new LinkedHashMap<>();
    boolean utf8 = false;

    int offset = 0;
    while (offset + DATASET_HEADER_LENGTH <= iim.length && (iim[offset] & 0xFF) == TAG_MARKER) {
      int record = iim[offset + 1] & 0xFF;
      int dataset = iim[offset + 2] & 0xFF;
      int valueStart = valueStart(iim, offset);
      int length = valueLength(iim, offset);
      if (valueStart < 0 || length < 0 || valueStart + length > iim.length) {
        break;
      }
      byte[] value = Arrays.copyOfRange(iim, valueStart, valueStart + length);
      offset = valueStart + length;

      if (IptcCharset.isUtf8Declaration(record, dataset, value)) {
        utf8 = true;
      } else if (record == IptcCharset.APPLICATION_RECORD) {
        values.computeIfAbsent(dataset, key -> new ArrayList<>()).add(value);
      }
    }
    return new RawFields(values, utf8);
  }

  private static int valueStart(byte[] iim, int offset) {
    int declared = declaredLength(iim, offset);
    if ((declared & EXTENDED_LENGTH_FLAG) == 0) {
      return offset + DATASET_HEADER_LENGTH;
    }
    int count = declared & ~EXTENDED_LENGTH_FLAG;
    if (count > MAX_EXTENDED_LENGTH_BYTES || offset + DATASET_HEADER_LENGTH + count > iim.length) {
      return -1;
    }
    return offset + DATASET_HEADER_LENGTH + count;
  }

  private static int valueLength(byte[] iim, int offset) {
    int declared = declaredLength(iim, offset);
    if ((declared & EXTENDED_LENGTH_FLAG) == 0) {
      return declared;
    }
    int count = declared & ~EXTENDED_LENGTH_FLAG;
    if (count > MAX_EXTENDED_LENGTH_BYTES || offset + DATASET_HEADER_LENGTH + count > iim.length) {
      return -1;
    }
    int length = 0;
    for (int i = 0; i < count; i++) {
      length = (length << 8) | (iim[offset + DATASET_HEADER_LENGTH + i] & 0xFF);
    }
    return length;
  }

  private static int declaredLength(byte[] iim, int offset) {
    return ((iim[offset + 3] & 0xFF) << 8) | (iim[offset + 4] & 0xFF);
  }

  private static Map<Integer, List<String>> decode(RawFields raw) {
    // Without an explicit 1:90 declaration the IIM default applies, which is ISO-8859-1.
    Charset charset = raw.utf8() ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
    Map<Integer, List<String>> fields = new LinkedHashMap<>();
    raw.values()
        .forEach(
            (dataset, values) ->
                fields.put(dataset, values.stream().map(v -> new String(v, charset)).toList()));
    return fields;
  }

  private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
    return offset + prefix.length <= data.length
        && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
  }

  private static int readUnsignedShort(byte[] data, int offset) {
    return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
  }

  private static int readInt(byte[] data, int offset) {
    return ((data[offset] & 0xFF) << 24)
        | ((data[offset + 1] & 0xFF) << 16)
        | ((data[offset + 2] & 0xFF) << 8)
        | (data[offset + 3] & 0xFF);
  }

  private record RawFields(Map<Integer, List<byte[]>> values, boolean utf8) {}
}
