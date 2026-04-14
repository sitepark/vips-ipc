package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VipsClientBuilderTest {

  // ── inProcess + build ─────────────────────────────────────────

  @Test
  void testBuildWithInProcessCreatesClosableClient() {
    assertDoesNotThrow(
        () -> VipsClient.builder().inProcess().build().close(),
        "inProcess().build() should create a VipsClient that can be closed without error");
  }

  @Test
  void testBuildWithInProcessTrueCreatesClosableClient() {
    assertDoesNotThrow(
        () -> VipsClient.builder().inProcess(true).build().close(),
        "inProcess(true).build() should create a VipsClient that can be closed without error");
  }

  // ── buildPool validation ──────────────────────────────────────

  @Test
  void testBuildPoolThrowsForPoolSizeLessThanOne() {
    assertThrows(
        IllegalArgumentException.class,
        () -> VipsClient.builder().buildPool(0),
        "buildPool(0) should throw IllegalArgumentException because pool size must be >= 1");
  }

  // ── inProcess + buildPool ─────────────────────────────────────

  @Test
  void testBuildPoolWithInProcessCreatesClosablePool() {
    assertDoesNotThrow(
        () -> VipsClient.builder().inProcess().buildPool(2).close(),
        "inProcess().buildPool(2) should create a VipsClientPool that can be closed without error");
  }
}
