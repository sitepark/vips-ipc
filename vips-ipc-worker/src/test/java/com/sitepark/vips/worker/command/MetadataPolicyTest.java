package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sitepark.vips.command.Metadata;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.UseConcurrentHashMap") // EnumMap: method-local test fixture
class MetadataPolicyTest {

  private static final String MARKER = "trainedAlgorithmicMedia";
  private static final int OBJECT_NAME = 5;
  private static final int COPYRIGHT_NOTICE = 116;
  private static final int CAPTION_ABSTRACT = 120;

  private static MetadataContext context(Map<XmpTag, List<String>> xmp, Metadata explicit) {
    return new MetadataContext(new SourceMetadata(xmp, new byte[0]), explicit);
  }

  private static Map<XmpTag, List<String>> sourceXmp() {
    Map<XmpTag, List<String>> xmp = new EnumMap<>(XmpTag.class);
    xmp.put(XmpTag.DIGITAL_SOURCE_TYPE, List.of(MARKER));
    return xmp;
  }

  /** The IPTC the policy would write, parsed back the way a standard reader sees it. */
  private static Map<Integer, List<String>> writtenIptc(Metadata explicit) {
    return IptcParser.parse(IptcBuilder.buildBlob(explicit));
  }

  @Test
  void testIptcIsWrittenFromTheExplicitMetadata() {
    assertEquals(
        Map.of(
            OBJECT_NAME, List.of("Titel"),
            COPYRIGHT_NOTICE, List.of("(c)"),
            CAPTION_ABSTRACT, List.of("Beschreibung")),
        writtenIptc(new Metadata("(c)", "Titel", "Beschreibung")),
        "The caller's metadata alone determines the IPTC block");
  }

  @Test
  void testIptcOmitsFieldsTheCallerLeftNull() {
    assertEquals(
        Map.of(OBJECT_NAME, List.of("Titel")),
        writtenIptc(new Metadata(null, "Titel", null)),
        "A null field should simply be absent, with no source value filling in for it");
  }

  @Test
  void testNoIptcIsWrittenWithoutExplicitMetadata() {
    assertEquals(
        0,
        IptcBuilder.buildBlob(null).length,
        "Without a Metadata parameter the output carries no IPTC at all");
  }

  @Test
  void testDigitalSourceTypeIsCarriedOverFromTheSource() {
    assertEquals(
        List.of(MARKER),
        MetadataPolicy.carriedXmp(context(sourceXmp(), null)).get(XmpTag.DIGITAL_SOURCE_TYPE),
        "DigitalSourceType is the one field copied from the source image");
  }

  @Test
  void testDigitalSourceTypeSurvivesAnExplicitMetadata() {
    MetadataContext ctx = context(sourceXmp(), new Metadata("(c)", "Titel", "Beschreibung"));

    assertEquals(
        List.of(MARKER),
        MetadataPolicy.carriedXmp(ctx).get(XmpTag.DIGITAL_SOURCE_TYPE),
        "The explicit metadata has no DigitalSourceType counterpart, so the copy must survive");
  }

  @Test
  void testNoXmpIsWrittenWhenTheSourceHasNoDigitalSourceType() {
    MetadataContext ctx =
        context(new EnumMap<>(XmpTag.class), new Metadata("(c)", "Titel", "Beschreibung"));

    assertEquals(
        0,
        XmpBuilder.buildPacket(MetadataPolicy.carriedXmp(ctx)).length,
        "The explicit metadata is not mirrored into XMP; without the marker there is no packet");
  }

  @Test
  void testEmptyContextProducesNeitherBlock() {
    MetadataContext empty = MetadataContext.empty();

    assertTrue(
        IptcBuilder.buildBlob(empty.explicit()).length == 0
            && XmpBuilder.buildPacket(MetadataPolicy.carriedXmp(empty)).length == 0,
        "Nothing to write should produce neither an IPTC block nor an XMP packet");
  }
}
