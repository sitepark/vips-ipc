package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XmpReaderTest {

  private static final String TRAINED_ALGORITHMIC_MEDIA =
      "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia";

  private static Path fixture(String name) throws URISyntaxException {
    return Path.of(XmpReaderTest.class.getResource("/" + name).toURI());
  }

  private static Map<XmpTag, List<String>> parseFixture(String name)
      throws IOException, URISyntaxException {
    return XmpReader.parse(Files.readAllBytes(fixture(name)));
  }

  @Test
  void testParseReadsDigitalSourceType() throws Exception {
    assertEquals(
        List.of(TRAINED_ALGORITHMIC_MEDIA),
        parseFixture("digital-source-type.xmp").get(XmpTag.DIGITAL_SOURCE_TYPE),
        "DigitalSourceType should be read from the IPTC extension namespace");
  }

  @Test
  void testParseReadsNothingButDigitalSourceType() throws Exception {
    // The fixture also carries dc:title, dc:creator, dc:subject and photoshop:Headline. None of
    // those is whitelisted any more, so the map must hold exactly the one entry.
    assertEquals(
        Map.of(XmpTag.DIGITAL_SOURCE_TYPE, List.of(TRAINED_ALGORITHMIC_MEDIA)),
        parseFixture("digital-source-type.xmp"),
        "Only DigitalSourceType is whitelisted; every other property must be ignored");
  }

  @Test
  void testParseMatchesOnNamespaceUriRatherThanPrefix() {
    // An XMP prefix is an arbitrary local binding. IPTC documents Iptc4xmpExt, but exiftool and
    // Adobe write iptcExt for the same namespace, and any other prefix is equally valid.
    String packet =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\""
            + " xmlns:iptcExt=\"http://iptc.org/std/Iptc4xmpExt/2008-02-29/\">"
            + "<iptcExt:DigitalSourceType>algorithmicMedia</iptcExt:DigitalSourceType>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta>";

    assertEquals(
        List.of("algorithmicMedia"),
        XmpReader.parse(packet.getBytes(StandardCharsets.UTF_8)).get(XmpTag.DIGITAL_SOURCE_TYPE),
        "A different prefix bound to the same namespace must still be recognised");
  }

  @Test
  void testParseIgnoresWhitelistedNameInAnotherNamespace() {
    String packet =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\" xmlns:other=\"http://example.com/ns/\">"
            + "<other:DigitalSourceType>nope</other:DigitalSourceType>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta>";

    assertTrue(
        XmpReader.parse(packet.getBytes(StandardCharsets.UTF_8)).isEmpty(),
        "The local name alone must not match; the namespace has to match too");
  }

  @Test
  void testParseReadsPropertyWrittenAsAttribute() {
    String packet =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\""
            + " xmlns:Iptc4xmpExt=\"http://iptc.org/std/Iptc4xmpExt/2008-02-29/\""
            + " Iptc4xmpExt:DigitalSourceType=\"algorithmicMedia\"/>"
            + "</rdf:RDF></x:xmpmeta>";

    assertEquals(
        List.of("algorithmicMedia"),
        XmpReader.parse(packet.getBytes(StandardCharsets.UTF_8)).get(XmpTag.DIGITAL_SOURCE_TYPE),
        "The RDF shorthand form, where a simple property is an attribute, should be read");
  }

  @Test
  void testParseReturnsEmptyForNullInput() {
    assertTrue(XmpReader.parse(null).isEmpty(), "A null packet should yield no fields");
  }

  @Test
  void testParseReturnsEmptyForEmptyInput() {
    assertTrue(XmpReader.parse(new byte[0]).isEmpty(), "An empty packet should yield no fields");
  }

  @Test
  void testParseReturnsEmptyForMalformedXml() {
    byte[] malformed = "<x:xmpmeta><rdf:RDF>".getBytes(StandardCharsets.UTF_8);

    assertTrue(
        XmpReader.parse(malformed).isEmpty(),
        "Malformed metadata must degrade to no fields rather than fail the image operation");
  }

  @Test
  void testParseRejectsExternalEntityDeclaration() throws Exception {
    Path secret = Files.createTempFile("xxe", ".txt");
    Files.writeString(secret, "TOP-SECRET");
    String packet =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file://"
            + secret.toAbsolutePath()
            + "\">]>"
            + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\""
            + " xmlns:Iptc4xmpExt=\"http://iptc.org/std/Iptc4xmpExt/2008-02-29/\">"
            + "<Iptc4xmpExt:DigitalSourceType>&xxe;</Iptc4xmpExt:DigitalSourceType>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta>";

    Map<XmpTag, List<String>> fields = XmpReader.parse(packet.getBytes(StandardCharsets.UTF_8));

    Files.delete(secret);
    assertFalse(
        fields.toString().contains("TOP-SECRET"),
        "A DOCTYPE declaration must be rejected outright so no external entity can be resolved");
  }
}
