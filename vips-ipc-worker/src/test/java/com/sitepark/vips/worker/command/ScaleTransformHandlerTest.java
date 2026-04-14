package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScaleTransformHandlerTest {

  @Test
  void testParseBackgroundWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground("FFFFFF");
    assertEquals(
        List.of(255.0, 255.0, 255.0, 0.0), result, "White hex should parse to {255,255,255,0}");
  }

  @Test
  void testParseBackgroundBlack() {
    List<Double> result = ScaleTransformSupport.parseBackground("000000");
    assertEquals(List.of(0.0, 0.0, 0.0, 0.0), result, "Black hex should parse to {0,0,0,0}");
  }

  @Test
  void testParseBackgroundRed() {
    List<Double> result = ScaleTransformSupport.parseBackground("FF0000");
    assertEquals(List.of(255.0, 0.0, 0.0, 0.0), result, "Red hex should parse to {255,0,0,0}");
  }

  @Test
  void testParseBackgroundNullDefaultsToTransparentWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground(null);
    assertEquals(
        List.of(255.0, 255.0, 255.0, 0.0),
        result,
        "Null background should default to transparent white");
  }

  @Test
  void testParseBackgroundInvalidDefaultsToTransparentWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground("ZZZZZZ");
    assertEquals(
        List.of(255.0, 255.0, 255.0, 0.0),
        result,
        "Invalid hex should default to transparent white");
  }

  @Test
  void testParseBackgroundTooShortDefaultsToTransparentWhite() {
    List<Double> result = ScaleTransformSupport.parseBackground("FFF");
    assertEquals(
        List.of(255.0, 255.0, 255.0, 0.0),
        result,
        "Too short hex should default to transparent white");
  }

  @Test
  void testParseBackgroundRgbaFromHex() {
    List<Double> result = ScaleTransformSupport.parseBackground("FF000080");
    assertEquals(
        List.of(255.0, 0.0, 0.0, 128.0),
        result,
        "8-digit hex should parse alpha from last two digits");
  }

  @Test
  void testClampCropDimensionWithinBounds() {
    int result = ScaleTransformSupport.clampCropDimension(100, 200, 0);
    assertEquals(100, result, "Requested dimension within image bounds should not be clamped");
  }

  @Test
  void testClampCropDimensionAtExactBoundary() {
    int result = ScaleTransformSupport.clampCropDimension(200, 200, 0);
    assertEquals(200, result, "Requested dimension equal to image size should not be clamped");
  }

  @Test
  void testClampCropDimensionExceedsBoundary() {
    int result = ScaleTransformSupport.clampCropDimension(201, 200, 0);
    assertEquals(
        200, result, "Requested dimension exceeding image size should be clamped to image size");
  }

  @Test
  void testClampCropDimensionWithOffsetExceedsBoundary() {
    int result = ScaleTransformSupport.clampCropDimension(100, 199, 100);
    assertEquals(
        99, result, "Crop region exceeding image boundary due to offset should be clamped");
  }

  @Test
  void testClampCropDimensionOffsetAtImageEdge() {
    int result = ScaleTransformSupport.clampCropDimension(10, 200, 200);
    assertEquals(0, result, "Offset at image edge should yield zero available dimension");
  }

  @Test
  void testClampCropDimensionOffsetBeyondImageEdge() {
    int result = ScaleTransformSupport.clampCropDimension(10, 200, 210);
    assertEquals(-10, result, "Offset beyond image edge should yield negative available dimension");
  }

  @Test
  void testClampCropDimensionWithNegativeOffset() {
    int result = ScaleTransformSupport.clampCropDimension(100, 200, -10);
    assertEquals(
        90, result, "Negative offset should reduce requested dimension by out-of-bounds portion");
  }

  @Test
  void testClampCropDimensionWithNegativeOffsetAbsorbsEntireRequest() {
    int result = ScaleTransformSupport.clampCropDimension(5, 200, -10);
    assertEquals(
        -5,
        result,
        "Negative offset larger than requested dimension should yield non-positive result");
  }

  @Test
  void testClampCropDimensionWithNegativeOffsetAndRightBoundaryClamping() {
    int result = ScaleTransformSupport.clampCropDimension(250, 200, -10);
    assertEquals(
        200,
        result,
        "Negative offset with large request should still be clamped to image size at the right");
  }
}
