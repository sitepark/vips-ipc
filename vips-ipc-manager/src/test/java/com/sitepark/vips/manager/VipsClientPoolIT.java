package com.sitepark.vips.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.sitepark.vips.command.Resize;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresVips
class VipsClientPoolIT {

  @SuppressWarnings("PMD.MutableStaticState")
  @TempDir
  static Path tempDir;

  private static VipsClientPool pool;

  @SuppressWarnings("PMD.LawOfDemeter")
  private static final Path SOURCE =
      Path.of(
          Objects.requireNonNull(
                  Thread.currentThread()
                      .getContextClassLoader()
                      .getResource("musterbild_hochkant_08.jpg"))
              .getPath());

  @BeforeAll
  static void startPool() throws IOException {
    pool = VipsClient.builder().concurrency(1).buildPool(2);
  }

  @AfterAll
  static void stopPool() {
    if (pool != null) {
      pool.close();
    }
  }

  // ── getEnvironment ────────────────────────────────────────────

  @Test
  void testGetEnvironmentReturnsNonNullResponse() throws IOException {
    assertThat(pool.getEnvironment())
        .as("getEnvironment() should return a non-null response")
        .isNotNull();
  }

  // ── resize ────────────────────────────────────────────────────

  @Test
  void testResizeProducesOutputFile() throws IOException {
    Path target = tempDir.resolve("pool_resized.jpg");
    pool.execute(Resize.of(SOURCE, target, 0.5));
    assertThat(Files.size(target))
        .as("resize() should produce a non-empty output file")
        .isGreaterThan(0L);
  }

  // ── parallel ──────────────────────────────────────────────────

  @Test
  void testParallelResizesAllProduceOutputFiles() {
    List<Path> targets =
        IntStream.range(0, 4).mapToObj(i -> tempDir.resolve("parallel_" + i + ".jpg")).toList();

    targets.parallelStream()
        .forEach(
            target -> {
              try {
                pool.execute(Resize.of(SOURCE, target, 0.5));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });

    long nonEmptyCount =
        targets.stream()
            .filter(
                t -> {
                  try {
                    return Files.size(t) > 0;
                  } catch (IOException e) {
                    return false;
                  }
                })
            .count();

    assertThat(nonEmptyCount)
        .as("All 4 parallel resize operations should produce non-empty output files")
        .isEqualTo(4L);
  }
}
