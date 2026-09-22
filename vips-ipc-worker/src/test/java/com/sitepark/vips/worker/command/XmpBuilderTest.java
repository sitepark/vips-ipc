package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XmpBuilderTest {

  private static String packetOf(Map<XmpTag, List<String>> fields) {
    return new String(XmpBuilder.buildPacket(fields), StandardCharsets.UTF_8);
  }

  @Test
  void testBuildPacketWithNoFieldsReturnsEmptyArray() {
    assertEquals(
        0, XmpBuilder.buildPacket(Map.of()).length, "No fields should produce no XMP packet");
  }

  @Test
  void testBuildPacketWithOnlyEmptyValueListsReturnsEmptyArray() {
    assertEquals(
        0,
        XmpBuilder.buildPacket(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of())).length,
        "A tag with no values should produce no XMP packet");
  }

  @Test
  void testBuildPacketWritesDigitalSourceType() {
    String packet = packetOf(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of("algorithmicMedia")));

    assertTrue(
        packet.contains(
            "<Iptc4xmpExt:DigitalSourceType>algorithmicMedia</Iptc4xmpExt:DigitalSourceType>"),
        "DigitalSourceType should be written as a simple property");
  }

  @Test
  void testBuildPacketDeclaresTheNamespaceItUses() {
    String packet = packetOf(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of("algorithmicMedia")));

    assertTrue(
        packet.contains("xmlns:Iptc4xmpExt=\"http://iptc.org/std/Iptc4xmpExt/2008-02-29/\""),
        "The IPTC extension namespace should be declared");
  }

  @Test
  void testBuildPacketEscapesMarkup() {
    String packet = packetOf(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of("a < b & c \"d\"")));

    assertTrue(packet.contains("a &lt; b &amp; c &quot;d&quot;"), "Markup should be escaped");
  }

  @Test
  void testBuildPacketDropsControlCharactersXmlForbids() {
    String packet = packetOf(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of("vor\u0000nach")));

    assertTrue(
        packet.contains(">vornach<"),
        "A NUL byte copied from source metadata would make the packet unparseable and is dropped");
  }

  @Test
  void testBuildPacketRoundTripsThroughReader() {
    Map<XmpTag, List<String>> fields =
        Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of("trainedAlgorithmicMedia"));

    assertEquals(
        fields,
        XmpReader.parse(XmpBuilder.buildPacket(fields)),
        "What the builder writes the reader should read back unchanged");
  }

  @Test
  void testBuildPacketIsDelimitedByXpacketInstructions() {
    String packet = packetOf(Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of("algorithmicMedia")));

    assertTrue(
        packet.startsWith("<?xpacket begin=") && packet.endsWith("<?xpacket end=\"w\"?>"),
        "The packet should be delimited by xpacket processing instructions");
  }
}
