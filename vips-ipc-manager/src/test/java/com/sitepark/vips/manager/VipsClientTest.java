package com.sitepark.vips.manager;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VipsClientTest {

  @Test
  @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
  void test() throws IOException {
    Path source =
        Path.of(
            "/home/veltrup/git/vips-ipc/vips-ipc-worker/src/test/resources/musterbild_hochkant_08.jpg");
    Path target = Path.of("/home/veltrup/git/vips-ipc/vips-ipc-worker/out.jpg");

    try (VipsClient client = VipsClient.builder().build()) {
      client.resize(source, target, 0.5);
    }
  }
}
