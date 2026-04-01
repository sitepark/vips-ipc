package com.sitepark.vips.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

class ResponseTest {

  // ── OkResponse ──────────────────────────────────────────────

  @Test
  void testOkResponseEquals() {
    EqualsVerifier.forClass(OkResponse.class).verify();
  }

  @Test
  void testSerializeOkResponse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"status\":\"ok\"}",
        mapper.writeValueAsString(new OkResponse()),
        "OkResponse should serialize with only the status discriminator");
  }

  @Test
  void testDeserializeOkResponse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new OkResponse(),
        mapper.readValue("{\"status\":\"ok\"}", Response.class),
        "OkResponse should deserialize via Response polymorphic type");
  }

  // ── ErrorResponse ────────────────────────────────────────────

  @Test
  void testErrorResponseEquals() {
    EqualsVerifier.forClass(ErrorResponse.class).verify();
  }

  @Test
  void testSerializeErrorResponse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"status\":\"error\",\"message\":\"something went wrong\",\"stackTrace\":\"at line 1\"}",
        mapper.writeValueAsString(new ErrorResponse("something went wrong", "at line 1")),
        "ErrorResponse should serialize with status discriminator and all fields");
  }

  @Test
  void testDeserializeErrorResponse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new ErrorResponse("something went wrong", "at line 1"),
        mapper.readValue(
            "{\"status\":\"error\",\"message\":\"something went wrong\",\"stackTrace\":\"at line"
                + " 1\"}",
            Response.class),
        "ErrorResponse should deserialize via Response polymorphic type");
  }

  // ── VipsEnvironmentResponse ──────────────────────────────────

  @Test
  void testVipsEnvironmentResponseEquals() {
    EqualsVerifier.forClass(VipsEnvironmentResponse.class).suppress(Warning.NULL_FIELDS).verify();
  }

  @Test
  void testSerializeVipsEnvironmentResponse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"status\":\"environment\",\"vipsVersion\":\"8.15.1\","
            + "\"supportedFormats\":[\"jpg\",\"png\"]}",
        mapper.writeValueAsString(new VipsEnvironmentResponse("8.15.1", List.of("jpg", "png"))),
        "VipsEnvironmentResponse should serialize with status discriminator and all fields");
  }

  @Test
  void testDeserializeVipsEnvironmentResponse() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new VipsEnvironmentResponse("8.15.1", List.of("jpg", "png")),
        mapper.readValue(
            "{\"status\":\"environment\",\"vipsVersion\":\"8.15.1\","
                + "\"supportedFormats\":[\"jpg\",\"png\"]}",
            Response.class),
        "VipsEnvironmentResponse should deserialize via Response polymorphic type");
  }

  @Test
  void testVipsEnvironmentResponseDefensiveCopy() {
    List<String> formats = new ArrayList<>(List.of("jpg", "png"));
    VipsEnvironmentResponse response = new VipsEnvironmentResponse("8.15.1", formats);
    formats.add("webp");
    assertEquals(
        new VipsEnvironmentResponse("8.15.1", List.of("jpg", "png")),
        response,
        "VipsEnvironmentResponse should be unaffected by mutation of the original list");
  }
}
