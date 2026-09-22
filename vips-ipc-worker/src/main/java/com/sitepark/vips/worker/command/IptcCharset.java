package com.sitepark.vips.worker.command;

import java.util.Arrays;

/**
 * The IIM {@code 1:90} CodedCharacterSet dataset.
 *
 * <p>IIM values default to ISO-8859-1. {@code ESC % G} selects UTF-8 and is what every modern writer
 * emits; without it a reader is entitled to interpret UTF-8 bytes as Latin-1 and render mojibake, so
 * the declaration is both parsed on read and always written back out.
 */
final class IptcCharset {

  /** Envelope record that carries the character-set declaration. */
  static final int ENVELOPE_RECORD = 1;

  /** Application2 record, which holds the descriptive datasets. */
  static final int APPLICATION_RECORD = 2;

  /** Dataset number of CodedCharacterSet. */
  static final int CODED_CHARACTER_SET = 90;

  /** {@code ESC % G} — the ISO 2022 escape sequence selecting UTF-8. */
  static final byte[] UTF8_DECLARATION = {0x1B, 0x25, 0x47};

  private IptcCharset() {}

  /** Whether the given dataset is a {@code 1:90} declaring UTF-8. */
  static boolean isUtf8Declaration(int record, int dataset, byte[] value) {
    return record == ENVELOPE_RECORD
        && dataset == CODED_CHARACTER_SET
        && Arrays.equals(value, UTF8_DECLARATION);
  }
}
