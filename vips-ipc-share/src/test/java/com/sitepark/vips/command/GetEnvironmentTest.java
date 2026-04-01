package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class GetEnvironmentTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(GetEnvironment.class).verify();
  }

  @Test
  void testSerialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"get-environment\"}",
        mapper.writeValueAsString(new GetEnvironment()),
        "GetEnvironment should serialize with only the command discriminator");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new GetEnvironment(),
        mapper.readValue("{\"command\":\"get-environment\"}", Command.class),
        "GetEnvironment should deserialize via Command polymorphic type");
  }
}
