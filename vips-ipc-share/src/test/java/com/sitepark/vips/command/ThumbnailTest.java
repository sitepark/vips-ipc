package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ThumbnailTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(Thumbnail.class).verify();
  }

  @Test
  void testSerialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        "{\"command\":\"thumbnail\",\"source\":\"/src.jpg\",\"target\":\"/dst.jpg\",\"width\":800}",
        mapper.writeValueAsString(new Thumbnail("/src.jpg", "/dst.jpg", 800)),
        "Thumbnail should serialize with command discriminator and all fields");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(
        new Thumbnail("/src.jpg", "/dst.jpg", 800),
        mapper.readValue(
            "{\"command\":\"thumbnail\",\"source\":\"/src.jpg\",\"target\":\"/dst.jpg\",\"width\":800}",
            Command.class),
        "Thumbnail should deserialize via Command polymorphic type");
  }
}
