package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class OutputFormatTest {

  // ── extension() ──────────────────────────────────────────────────

  @Test
  void testJpgExtension() {
    assertEquals("jpg", OutputFormat.jpeg().extension(), "JpgFormat extension should be 'jpg'");
  }

  @Test
  void testWebpExtension() {
    assertEquals("webp", OutputFormat.webp().extension(), "WebpFormat extension should be 'webp'");
  }

  @Test
  void testPngExtension() {
    assertEquals("png", OutputFormat.png().extension(), "PngFormat extension should be 'png'");
  }

  @Test
  void testGifExtension() {
    assertEquals("gif", OutputFormat.gif().extension(), "GifFormat extension should be 'gif'");
  }

  @Test
  void testAvifExtension() {
    assertEquals("avif", OutputFormat.avif().extension(), "AvifFormat extension should be 'avif'");
  }

  // ── equals/hashCode contracts ─────────────────────────────────────

  @Test
  void testJpgEquals() {
    EqualsVerifier.forClass(OutputFormat.JpegFormat.class).verify();
  }

  @Test
  void testWebpEquals() {
    EqualsVerifier.forClass(OutputFormat.WebpFormat.class).verify();
  }

  @Test
  void testPngEquals() {
    EqualsVerifier.forClass(OutputFormat.PngFormat.class).verify();
  }

  @Test
  void testGifEquals() {
    EqualsVerifier.forClass(OutputFormat.GifFormat.class).verify();
  }

  @Test
  void testAvifEquals() {
    EqualsVerifier.forClass(OutputFormat.AvifFormat.class).verify();
  }

  // ── defaults ─────────────────────────────────────────────────────

  @Test
  void testJpgDefaultQuality() {
    assertEquals(
        OutputFormat.DEFAULT_QUALITY,
        OutputFormat.jpeg().quality(),
        "jpg() should default quality to 82");
  }

  @Test
  void testJpgDefaultInterlaceIsTrue() {
    assertEquals(true, OutputFormat.jpeg().interlace(), "jpg() should default interlace to true");
  }

  @Test
  void testJpgDefaultStripIsFalse() {
    assertEquals(false, OutputFormat.jpeg().strip(), "jpg() should default strip to false");
  }

  @Test
  void testJpgDefaultAppendExtensionIsTrue() {
    assertEquals(
        true,
        OutputFormat.jpeg().appendExtension(),
        "jpg() should default appendExtension to true");
  }

  // ── with* derivation ──────────────────────────────────────────────

  @Test
  void testJpgWithQuality() {
    assertEquals(
        new OutputFormat.JpegFormat(75, true, false, true),
        OutputFormat.jpeg().withQuality(75),
        "withQuality() should return JpgFormat with updated quality");
  }

  @Test
  void testJpgWithInterlaceFalse() {
    assertEquals(
        new OutputFormat.JpegFormat(82, false, false, true),
        OutputFormat.jpeg().withInterlace(false),
        "withInterlace(false) should return JpgFormat with interlace=false");
  }

  @Test
  void testJpgWithStripTrue() {
    assertEquals(
        new OutputFormat.JpegFormat(82, true, true, true),
        OutputFormat.jpeg().withStrip(true),
        "withStrip(true) should return JpgFormat with strip=true");
  }

  @Test
  void testWebpWithLosslessTrue() {
    assertEquals(
        OutputFormat.webpLossless(),
        OutputFormat.webp().withLossless(true),
        "withLossless(true) should return the same as webpLossless()");
  }

  @Test
  void testPngWithStripTrue() {
    assertEquals(
        new OutputFormat.PngFormat(true, true),
        OutputFormat.png().withStrip(true),
        "withStrip(true) should return PngFormat with strip=true");
  }

  // ── serialization ─────────────────────────────────────────────────

  @Test
  void testJpgSerializesWithOnlyType() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"jpg\"}",
        mapper.writeValueAsString(OutputFormat.jpeg()),
        "jpg() with all defaults should serialize to {\"type\":\"jpg\"}");
  }

  @Test
  void testJpgSerializesQualityWhenNotDefault() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"jpg\",\"quality\":75}",
        mapper.writeValueAsString(OutputFormat.jpeg(75)),
        "jpg(75) should include quality in JSON");
  }

  @Test
  void testJpgSerializesInterlaceWhenFalse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"jpg\",\"interlace\":false}",
        mapper.writeValueAsString(OutputFormat.jpeg().withInterlace(false)),
        "jpg().withInterlace(false) should include interlace=false in JSON");
  }

  @Test
  void testJpgSerializesStripWhenTrue() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"jpg\",\"strip\":true}",
        mapper.writeValueAsString(OutputFormat.jpeg().withStrip(true)),
        "jpg().withStrip(true) should include strip=true in JSON");
  }

  @Test
  void testWebpSerializesWithOnlyType() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"webp\"}",
        mapper.writeValueAsString(OutputFormat.webp()),
        "webp() with all defaults should serialize to {\"type\":\"webp\"}");
  }

  @Test
  void testWebpSerializesQualityWhenNotDefault() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"webp\",\"quality\":75}",
        mapper.writeValueAsString(OutputFormat.webp(75)),
        "webp(75) should include quality in JSON");
  }

  @Test
  void testWebpSerializesLosslessWhenTrue() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"webp\",\"lossless\":true}",
        mapper.writeValueAsString(OutputFormat.webpLossless()),
        "webpLossless() should include lossless=true in JSON");
  }

  @Test
  void testPngSerializesWithOnlyType() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"png\"}",
        mapper.writeValueAsString(OutputFormat.png()),
        "png() with all defaults should serialize to {\"type\":\"png\"}");
  }

  @Test
  void testGifSerializesWithOnlyType() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"gif\"}",
        mapper.writeValueAsString(OutputFormat.gif()),
        "gif() with all defaults should serialize to {\"type\":\"gif\"}");
  }

  @Test
  void testAvifSerializesWithOnlyType() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"avif\"}",
        mapper.writeValueAsString(OutputFormat.avif()),
        "avif() with all defaults should serialize to {\"type\":\"avif\"}");
  }

  @Test
  void testAvifSerializesLosslessWhenTrue() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"avif\",\"lossless\":true}",
        mapper.writeValueAsString(OutputFormat.avifLossless()),
        "avifLossless() should include lossless=true in JSON");
  }

  // ── deserialization ───────────────────────────────────────────────

  @Test
  void testDeserializeJpgMinimal() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.jpeg(),
        mapper.readValue("{\"type\":\"jpg\"}", OutputFormat.class),
        "Minimal {\"type\":\"jpg\"} should deserialize to jpg() with defaults");
  }

  @Test
  void testDeserializeJpgWithQuality() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.jpeg(90),
        mapper.readValue("{\"type\":\"jpg\",\"quality\":90}", OutputFormat.class),
        "Should deserialize quality=90 correctly");
  }

  @Test
  void testDeserializeJpgWithInterlaceFalse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.jpeg().withInterlace(false),
        mapper.readValue("{\"type\":\"jpg\",\"interlace\":false}", OutputFormat.class),
        "Should deserialize interlace=false correctly");
  }

  @Test
  void testDeserializeJpgWithStripTrue() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.jpeg().withStrip(true),
        mapper.readValue("{\"type\":\"jpg\",\"strip\":true}", OutputFormat.class),
        "Should deserialize strip=true correctly");
  }

  @Test
  void testDeserializeWebpMinimal() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.webp(),
        mapper.readValue("{\"type\":\"webp\"}", OutputFormat.class),
        "Minimal {\"type\":\"webp\"} should deserialize to webp() with defaults");
  }

  @Test
  void testDeserializeWebpWithQuality() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.webp(75),
        mapper.readValue("{\"type\":\"webp\",\"quality\":75}", OutputFormat.class),
        "Should deserialize webp with quality=75 correctly");
  }

  @Test
  void testDeserializeAvifMinimal() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.avif(),
        mapper.readValue("{\"type\":\"avif\"}", OutputFormat.class),
        "Minimal {\"type\":\"avif\"} should deserialize to avif() with defaults");
  }

  @Test
  void testDeserializeAvifLossless() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.avifLossless(),
        mapper.readValue("{\"type\":\"avif\",\"lossless\":true}", OutputFormat.class),
        "Should deserialize avif with lossless=true correctly");
  }

  @Test
  void testDeserializePngMinimal() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.png(),
        mapper.readValue("{\"type\":\"png\"}", OutputFormat.class),
        "Minimal {\"type\":\"png\"} should deserialize to png() with defaults");
  }

  @Test
  void testDeserializeGifMinimal() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.gif(),
        mapper.readValue("{\"type\":\"gif\"}", OutputFormat.class),
        "Minimal {\"type\":\"gif\"} should deserialize to gif() with defaults");
  }
}
