package com.sitepark.vips.worker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sitepark.vips.command.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerConfigTest {

  private WorkerConfig config;

  @BeforeEach
  void setUp() {
    this.config = new WorkerConfig();
  }

  @Test
  void testDefaultJpegInterlaceIsTrue() {
    assertTrue(this.config.jpegInterlace(), "Default jpegInterlace should be true");
  }

  @Test
  void testDefaultStripIsFalse() {
    assertFalse(this.config.strip(), "Default strip should be false");
  }

  @Test
  void testApplyJpegInterlaceFalse() {
    this.config.apply(new Config(false, null));
    assertFalse(this.config.jpegInterlace(), "apply() should update jpegInterlace to false");
  }

  @Test
  void testApplyStripTrue() {
    this.config.apply(new Config(null, true));
    assertTrue(this.config.strip(), "apply() should update strip to true");
  }

  @Test
  void testApplyNullJpegInterlaceKeepsDefault() {
    this.config.apply(new Config(null, null));
    assertTrue(
        this.config.jpegInterlace(),
        "apply() with null jpegInterlace should keep default value (true)");
  }

  @Test
  void testApplyNullStripKeepsDefault() {
    this.config.apply(new Config(null, null));
    assertFalse(this.config.strip(), "apply() with null strip should keep default value (false)");
  }
}
