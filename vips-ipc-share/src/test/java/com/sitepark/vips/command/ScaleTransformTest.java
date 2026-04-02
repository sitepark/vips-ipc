package com.sitepark.vips.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jparams.verifier.tostring.ToStringVerifier;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.CropStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ScaleTransformTest {

  @Test
  void testEquals() {
    EqualsVerifier.forClass(ScaleTransform.class).verify();
  }

  @Test
  void testToString() {
    ToStringVerifier.forClass(ScaleTransform.class).verify();
  }

  @Test
  void testResizeStepEquals() {
    EqualsVerifier.forClass(ResizeStep.class).verify();
  }

  @Test
  void testBorderStepEquals() {
    EqualsVerifier.forClass(BorderStep.class).verify();
  }

  @Test
  void testCropStepEquals() {
    EqualsVerifier.forClass(CropStep.class).verify();
  }

  @Test
  void testSerializeWithAllSteps() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    ScaleTransform cmd =
        new ScaleTransform(
            "/src.jpg",
            "/dst/image",
            new ResizeStep(200, 100),
            new BorderStep(5, 5),
            new CropStep(190, 90, 5, 5),
            "FF0000",
            List.of(OutputFormat.of(OutputFormatType.JPG), OutputFormat.of(OutputFormatType.WEBP)),
            false);

    String json = mapper.writeValueAsString(cmd);

    assertEquals(
        "{\"command\":\"scale-transform\","
            + "\"source\":\"/src.jpg\",\"target\":\"/dst/image\","
            + "\"resize\":{\"width\":200,\"height\":100},"
            + "\"border\":{\"x\":5,\"y\":5},"
            + "\"crop\":{\"width\":190,\"height\":90,\"offsetX\":5,\"offsetY\":5},"
            + "\"background\":\"FF0000\","
            + "\"formats\":[{\"type\":\"jpg\"},{\"type\":\"webp\"}]}",
        json,
        "All fields including command discriminator and formats list should be serialized");
  }

  @Test
  void testSerializeWithNullStepsOmitsFields() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    ScaleTransform cmd =
        new ScaleTransform(
            "/src.jpg",
            "/dst/image",
            null,
            null,
            null,
            null,
            List.of(OutputFormat.of(OutputFormatType.JPG)),
            false);

    String json = mapper.writeValueAsString(cmd);

    assertEquals(
        "{\"command\":\"scale-transform\","
            + "\"source\":\"/src.jpg\",\"target\":\"/dst/image\","
            + "\"formats\":[{\"type\":\"jpg\"}]}",
        json,
        "Null steps should be omitted, formats list must be present");
  }

  @Test
  void testDeserialize() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();

    String json =
        "{\"command\":\"scale-transform\",\"source\":\"/src.jpg\",\"target\":\"/dst/image\","
            + "\"resize\":{\"width\":200,\"height\":100},"
            + "\"formats\":[{\"type\":\"jpg\"},{\"type\":\"avif\"}]}";

    Command cmd = mapper.readValue(json, Command.class);

    ScaleTransform expected =
        new ScaleTransform(
            "/src.jpg",
            "/dst/image",
            new ResizeStep(200, 100),
            null,
            null,
            null,
            List.of(OutputFormat.of(OutputFormatType.JPG), OutputFormat.of(OutputFormatType.AVIF)),
            false);

    assertEquals(expected, cmd, "Deserialized ScaleTransform should match expected");
  }
}
