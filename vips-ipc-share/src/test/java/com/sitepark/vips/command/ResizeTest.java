package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ResizeTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(Resize.class).verify();
  }

  @Test
  void testSerialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"resize\",\"source\":\"/src.jpg\",\"target\":\"/dst.jpg\",\"scale\":0.5}",
        mapper.writeValueAsString(new Resize("/src.jpg", "/dst.jpg", 0.5)),
        "Resize should serialize with command discriminator and all fields");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new Resize("/src.jpg", "/dst.jpg", 0.5),
        mapper.readValue(
            "{\"command\":\"resize\",\"source\":\"/src.jpg\",\"target\":\"/dst.jpg\",\"scale\":0.5}",
            Command.class),
        "Resize should deserialize via Command polymorphic type");
  }
}
