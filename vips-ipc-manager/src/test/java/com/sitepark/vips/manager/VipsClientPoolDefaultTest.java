package com.sitepark.vips.manager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class VipsClientPoolDefaultTest {

  @Test
  void testGetDefaultReturnsNonNull() {
    assertNotNull(VipsClientPool.getDefault(), "getDefault() should return a non-null pool");
  }

  @Test
  void testGetDefaultReturnsSameInstance() {
    assertSame(
        VipsClientPool.getDefault(),
        VipsClientPool.getDefault(),
        "getDefault() should always return the same pool instance");
  }
}
