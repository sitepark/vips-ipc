package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ShutdownTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(Shutdown.class).verify();
  }

  @Test
  void testSerialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"shutdown\"}",
        mapper.writeValueAsString(new Shutdown()),
        "Shutdown should serialize with only the command discriminator");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new Shutdown(),
        mapper.readValue("{\"command\":\"shutdown\"}", Command.class),
        "Shutdown should deserialize via Command polymorphic type");
  }
}
