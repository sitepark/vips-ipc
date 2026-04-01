package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScaleTransformHandlerTest {

  @Test
  void testParseBackgroundWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground("FFFFFF");
    assertEquals(List.of(255.0, 255.0, 255.0), result, "White hex should parse to {255,255,255}");
  }

  @Test
  void testParseBackgroundBlack() {
    List<Double> result = ScaleTransformSupport.parseBackground("000000");
    assertEquals(List.of(0.0, 0.0, 0.0), result, "Black hex should parse to {0,0,0}");
  }

  @Test
  void testParseBackgroundRed() {
    List<Double> result = ScaleTransformSupport.parseBackground("FF0000");
    assertEquals(List.of(255.0, 0.0, 0.0), result, "Red hex should parse to {255,0,0}");
  }

  @Test
  void testParseBackgroundNullDefaultsToWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground(null);
    assertEquals(List.of(255.0, 255.0, 255.0), result, "Null background should default to white");
  }

  @Test
  void testParseBackgroundInvalidDefaultsToWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground("ZZZZZZ");
    assertEquals(List.of(255.0, 255.0, 255.0), result, "Invalid hex should default to white");
  }

  @Test
  void testParseBackgroundTooShortDefaultsToWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground("FFF");
    assertEquals(List.of(255.0, 255.0, 255.0), result, "Too short hex should default to white");
  }
}
