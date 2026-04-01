package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(Config.class).verify();
  }

  @Test
  void testSerializeWithBothNull() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"config\"}",
        mapper.writeValueAsString(new Config(null, null)),
        "Config with both null fields should omit optional fields from JSON");
  }

  @Test
  void testSerializeWithJpegInterlace() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"config\",\"jpegInterlace\":true}",
        mapper.writeValueAsString(new Config(true, null)),
        "Config should serialize only jpegInterlace when strip is null");
  }

  @Test
  void testSerializeWithStrip() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"config\",\"strip\":false}",
        mapper.writeValueAsString(new Config(null, false)),
        "Config should serialize only strip when jpegInterlace is null");
  }

  @Test
  void testSerializeWithBothFields() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"config\",\"jpegInterlace\":true,\"strip\":false}",
        mapper.writeValueAsString(new Config(true, false)),
        "Config should serialize both fields when both are non-null");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new Config(true, false),
        mapper.readValue(
            "{\"command\":\"config\",\"jpegInterlace\":true,\"strip\":false}", Command.class),
        "Config should deserialize via Command polymorphic type");
  }
}
