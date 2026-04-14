package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jparams.verifier.tostring.ToStringVerifier;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ScaleTransformBatchTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(ScaleTransformBatch.class).verify();
  }

  @Test
  void testToString() {
    ToStringVerifier.forClass(ScaleTransformBatch.class).verify();
  }

  @Test
  void testBatchTargetEquals() {
    EqualsVerifier.forClass(BatchTarget.class).verify();
  }

  @Test
  void testSerialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    ScaleTransformBatch cmd =
        new ScaleTransformBatch(
            "/src.jpg",
            List.of(
                new BatchTarget(
                    "/out/large",
                    new ResizeStep(800, 600),
                    null,
                    null,
                    null,
                    List.of(OutputFormat.of(OutputFormatType.JPG)),
                    null),
                new BatchTarget(
                    "/out/small",
                    new ResizeStep(200, 150),
                    null,
                    null,
                    null,
                    List.of(
                        OutputFormat.of(OutputFormatType.JPG),
                        OutputFormat.of(OutputFormatType.WEBP)),
                    null)),
            false);

    String json = mapper.writeValueAsString(cmd);

    assertEquals(
        "{\"command\":\"scale-transform-batch\","
            + "\"source\":\"/src.jpg\","
            + "\"targets\":["
            + "{\"target\":\"/out/large\","
            + "\"resize\":{\"width\":800,\"height\":600},\"formats\":[{\"type\":\"jpg\"}]},"
            + "{\"target\":\"/out/small\","
            + "\"resize\":{\"width\":200,\"height\":150},"
            + "\"formats\":[{\"type\":\"jpg\"},{\"type\":\"webp\"}]}"
            + "]}",
        json,
        "Batch command should serialize with all targets and formats");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();

    String json =
        "{\"command\":\"scale-transform-batch\","
            + "\"source\":\"/src.jpg\","
            + "\"targets\":["
            + "{\"target\":\"/out/large\","
            + "\"resize\":{\"width\":800,\"height\":600},"
            + "\"formats\":[{\"type\":\"jpg\"},{\"type\":\"avif\"}]}"
            + "]}";

    Command cmd = mapper.readValue(json, Command.class);

    ScaleTransformBatch expected =
        new ScaleTransformBatch(
            "/src.jpg",
            List.of(
                new BatchTarget(
                    "/out/large",
                    new ResizeStep(800, 600),
                    null,
                    null,
                    null,
                    List.of(
                        OutputFormat.of(OutputFormatType.JPG),
                        OutputFormat.of(OutputFormatType.AVIF)),
                    null)),
            false);
    assertEquals(expected, cmd, "Deserialized batch command should match expected");
  }
}
