package com.sitepark.vips.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.command.Thumbnail;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresVips
class VipsClientIT {

  @SuppressWarnings("PMD.MutableStaticState")
  @TempDir
  static Path tempDir;

  private static VipsClient client;

  @SuppressWarnings("PMD.LawOfDemeter")
  private static final Path SOURCE =
      Path.of(
          Objects.requireNonNull(
                  Thread.currentThread()
                      .getContextClassLoader()
                      .getResource("musterbild_hochkant_08.jpg"))
              .getPath());

  @BeforeAll
  static void startClient() throws IOException {
    client = VipsClient.builder().concurrency(1).build();
  }

  @AfterAll
  static void stopClient() {
    if (client != null) {
      client.close();
    }
  }

  // ── getEnvironment ────────────────────────────────────────────

  @Test
  void testGetEnvironmentReturnsNonNullResponse() throws IOException {
    VipsEnvironmentResponse env = client.getEnvironment();
    assertThat(env).as("getEnvironment() should return a non-null response").isNotNull();
  }

  @Test
  @SuppressWarnings("PMD.LawOfDemeter")
  void testGetEnvironmentReturnsNonEmptyVipsVersion() throws IOException {
    VipsEnvironmentResponse env = client.getEnvironment();
    assertThat(env.vipsVersion())
        .as("vipsVersion should be a non-blank version string")
        .isNotBlank();
  }

  @Test
  @SuppressWarnings("PMD.LawOfDemeter")
  void testGetEnvironmentReturnsSupportedFormats() throws IOException {
    VipsEnvironmentResponse env = client.getEnvironment();
    assertThat(env.supportedFormats())
        .as("supportedFormats should contain at least one format")
        .isNotEmpty();
  }

  // ── resize ────────────────────────────────────────────────────

  @Test
  void testResizeProducesOutputFile() throws IOException {
    Path target = tempDir.resolve("resized.jpg");
    client.execute(Resize.of(SOURCE, target, 0.5));
    assertThat(Files.size(target))
        .as("resize() should produce a non-empty output file")
        .isGreaterThan(0L);
  }

  // ── thumbnail ────────────────────────────────────────────────

  @Test
  void testThumbnailProducesOutputFile() throws IOException {
    Path target = tempDir.resolve("thumbnail.jpg");
    client.execute(Thumbnail.of(SOURCE, target, 100));
    assertThat(Files.size(target))
        .as("thumbnail() should produce a non-empty output file")
        .isGreaterThan(0L);
  }

  // ── scaleTransform ────────────────────────────────────────────

  @Test
  void testScaleTransformProducesJpgOutputFile() throws IOException {
    Path targetBase = tempDir.resolve("scaled");
    client.execute(
        ScaleTransform.of(
            SOURCE,
            targetBase,
            new ScaleTransform.ResizeStep(200, 150),
            null,
            null,
            null,
            List.of(OutputFormat.jpeg()),
            null));
    assertThat(Files.size(tempDir.resolve("scaled.jpg")))
        .as("scaleTransform() should produce a non-empty JPG output file")
        .isGreaterThan(0L);
  }

  @Test
  void testScaleTransformProducesWebpOutputFile() throws IOException {
    Path targetBase = tempDir.resolve("scaled_webp");
    client.execute(
        ScaleTransform.of(
            SOURCE,
            targetBase,
            new ScaleTransform.ResizeStep(200, 150),
            null,
            null,
            null,
            List.of(OutputFormat.webp()),
            null));
    assertThat(Files.size(tempDir.resolve("scaled_webp.webp")))
        .as("scaleTransform() should produce a non-empty WebP output file")
        .isGreaterThan(0L);
  }
}
