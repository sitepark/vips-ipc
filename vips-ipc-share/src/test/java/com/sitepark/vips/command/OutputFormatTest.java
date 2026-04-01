package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class OutputFormatTest {

  // ── OutputFormatType (enum) ───────────────────────────────────

  @Test
  void testOutputFormatTypeGifExtension() {
    assertEquals("gif", OutputFormatType.GIF.extension(), "GIF extension should be 'gif'");
  }

  @Test
  void testOutputFormatTypePngExtension() {
    assertEquals("png", OutputFormatType.PNG.extension(), "PNG extension should be 'png'");
  }

  @Test
  void testOutputFormatTypeJpgExtension() {
    assertEquals("jpg", OutputFormatType.JPG.extension(), "JPG extension should be 'jpg'");
  }

  @Test
  void testOutputFormatTypeWebpExtension() {
    assertEquals("webp", OutputFormatType.WEBP.extension(), "WEBP extension should be 'webp'");
  }

  @Test
  void testOutputFormatTypeAvifExtension() {
    assertEquals("avif", OutputFormatType.AVIF.extension(), "AVIF extension should be 'avif'");
  }

  @Test
  void testOutputFormatTypeFromExtension() {
    assertEquals(
        OutputFormatType.JPG, OutputFormatType.fromExtension("jpg"), "Should resolve 'jpg' to JPG");
  }

  @Test
  void testOutputFormatTypeFromExtensionUnknownThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OutputFormatType.fromExtension("bmp"),
        "Unknown extension should throw IllegalArgumentException");
  }

  // ── OutputFormat (record) ─────────────────────────────────────

  @Test
  void testEquals() {
    EqualsVerifier.forClass(OutputFormat.class).verify();
  }

  @Test
  void testExtensionDelegatesToType() {
    OutputFormat fmt = OutputFormat.of(OutputFormatType.WEBP);
    assertEquals("webp", fmt.extension(), "extension() should delegate to OutputFormatType");
  }

  @Test
  void testEffectiveQualityDefaultsTo82() {
    OutputFormat fmt = OutputFormat.of(OutputFormatType.JPG);
    assertEquals(82, fmt.effectiveQuality(), "effectiveQuality() should default to 82 when null");
  }

  @Test
  void testEffectiveQualityExplicit() {
    OutputFormat fmt = OutputFormat.of(OutputFormatType.JPG, 75);
    assertEquals(75, fmt.effectiveQuality(), "effectiveQuality() should return the explicit value");
  }

  @Test
  void testSerializeWithoutQuality() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"jpg\"}",
        mapper.writeValueAsString(OutputFormat.of(OutputFormatType.JPG)),
        "OutputFormat without quality should omit quality field");
  }

  @Test
  void testSerializeWithQuality() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"type\":\"webp\",\"quality\":75}",
        mapper.writeValueAsString(OutputFormat.of(OutputFormatType.WEBP, 75)),
        "OutputFormat with quality should include quality field");
  }

  @Test
  void testDeserializeWithoutQuality() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.of(OutputFormatType.AVIF),
        mapper.readValue("{\"type\":\"avif\"}", OutputFormat.class),
        "Should deserialize to OutputFormat with null quality");
  }

  @Test
  void testDeserializeWithQuality() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        OutputFormat.of(OutputFormatType.JPG, 90),
        mapper.readValue("{\"type\":\"jpg\",\"quality\":90}", OutputFormat.class),
        "Should deserialize to OutputFormat with explicit quality");
  }
}
