package com.sitepark.vips.manager;

import com.sitepark.vips.command.*;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * A thread-safe pool of worker processes for parallel image processing.
 *
 * <p>Each pool entry is an independent worker subprocess. Callers borrow a free worker, execute a
 * command, and the worker is automatically returned to the pool — even if the command throws. If no
 * worker is available, the caller blocks until one is returned.
 *
 * <p>Typical usage with {@code parallelStream()}:
 *
 * <pre>{@code
 * int cores = Runtime.getRuntime().availableProcessors();
 * try (VipsClientPool pool = VipsClient.builder().buildPool(cores)) {
 *   files.parallelStream().forEach(f -> {
 *     try { pool.resize(f, output, 0.5); } catch (IOException e) { ... }
 *   });
 * }
 * }</pre>
 *
 * <p><b>Performance note:</b> {@code VIPS_CONCURRENCY} controls only the image computation phase
 * (resize, transform). Codec operations (JPEG/PNG encode/decode) are largely single-threaded
 * regardless of {@code VIPS_CONCURRENCY}. This means a pool increases throughput even with the
 * default concurrency setting, because while one worker is encoding, others can run computations in
 * parallel. For maximum throughput with many small images, combine {@code .concurrency(1)} with a
 * pool sized to the number of CPU cores.
 *
 * <p><b>Note on {@link #configure}:</b> {@code configure()} affects only the worker that is
 * currently borrowed. Use {@link #configureAll} to apply settings to all workers before starting
 * parallel processing.
 *
 * <p><b>Shutdown:</b> {@link #close()} shuts down all idle workers. The typical {@code
 * try-with-resources} pattern with {@code parallelStream().forEach()} is safe, because {@code
 * forEach()} blocks until all tasks complete before {@code close()} is entered.
 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.DoNotUseThreads"})
public class VipsClientPool implements AutoCloseable {

  public static final int DEFAULT_NICE_LEVEL = 15;

  private static final Logger LOG = Logger.getLogger(VipsClientPool.class.getName());

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private static final class DefaultHolder {
    static final VipsClientPool INSTANCE;

    static {
      try {
        INSTANCE =
            VipsClient.builder()
                .niceLevel(DEFAULT_NICE_LEVEL)
                .buildPool(Runtime.getRuntime().availableProcessors());
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }

  /**
   * Returns the application-wide default pool, shared across all callers. The pool size equals
   * {@link Runtime#availableProcessors()}. The instance is created lazily on first access and never
   * replaced.
   */
  public static VipsClientPool getDefault() {
    return DefaultHolder.INSTANCE;
  }

  private final BlockingQueue<WorkerBackend> pool;

  @FunctionalInterface
  private interface WorkerAction<T> {
    T apply(WorkerBackend worker) throws IOException;
  }

  VipsClientPool(List<WorkerBackend> workers) {
    this.pool = new ArrayBlockingQueue<>(workers.size(), true);
    this.pool.addAll(workers);
  }

  // ── Core helper ───────────────────────────────────────────────

  private <T> T execute(WorkerAction<T> action) throws IOException {
    WorkerBackend worker;
    try {
      worker = pool.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for a free worker", e);
    }
    try {
      return action.apply(worker);
    } finally {
      try {
        pool.put(worker);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warning("Interrupted while returning worker to pool; pool size reduced by one");
      }
    }
  }

  // ── Public API ────────────────────────────────────────────────

  /**
   * Returns information about the libvips installation on this system, including the version and a
   * list of available image format loaders. Use this to verify prerequisites before processing
   * images.
   */
  public VipsEnvironmentResponse getEnvironment() throws IOException {
    return execute(WorkerBackend::queryEnvironment);
  }

  /**
   * Sets global encoding parameters for subsequent image write operations on one worker.
   *
   * <p>Only non-null fields are applied; pass {@code null} to keep the current value. Note: this
   * affects only the worker currently borrowed from the pool. Use {@link #configureAll} to apply
   * settings to all workers in the pool.
   *
   * @param jpegInterlace {@code true} for progressive JPEG, {@code false} for baseline, or {@code
   *     null} to keep current
   * @param strip {@code true} to strip all metadata (EXIF, IPTC, XMP), or {@code null} to keep
   *     current
   */
  public void configure(Boolean jpegInterlace, Boolean strip) throws IOException {
    execute(
        worker -> {
          worker.execute(new Config(jpegInterlace, strip));
          return null;
        });
  }

  /**
   * Sets global encoding parameters on <em>all</em> workers in the pool.
   *
   * <p>Calls {@link #configure} once per worker sequentially. This method assumes no concurrent
   * operations are in progress; call it before starting parallel processing to ensure consistent
   * encoding settings across all workers.
   *
   * @param jpegInterlace {@code true} for progressive JPEG, {@code false} for baseline, or {@code
   *     null} to keep current
   * @param strip {@code true} to strip all metadata (EXIF, IPTC, XMP), or {@code null} to keep
   *     current
   */
  public void configureAll(Boolean jpegInterlace, Boolean strip) throws IOException {
    int size = pool.size();
    for (int i = 0; i < size; i++) {
      configure(jpegInterlace, strip);
    }
  }

  /** Scale an image by factor (0.5 = 50%). */
  public void resize(Path source, Path target, double scale) throws IOException {
    resize(source, target, scale, false);
  }

  /**
   * Scale an image by factor (0.5 = 50%).
   *
   * @param debug if {@code true}, the response includes a {@code DebugInfo} object with the
   *     equivalent vips CLI command
   */
  public void resize(Path source, Path target, double scale, boolean debug) throws IOException {
    execute(
        worker -> {
          worker.execute(
              new Resize(
                  source.toAbsolutePath().toString(),
                  target.toAbsolutePath().toString(),
                  scale,
                  debug));
          return null;
        });
  }

  /**
   * Extracts metadata from an image, including dimensions, channel count, alpha presence, and
   * optionally a quantized color palette.
   *
   * @param colorsPaletteBitDepth bit depth for GIF-based quantization (e.g. 5 → 32 palette slots);
   *     pass 0 to skip palette extraction
   */
  public ExtractResult extract(Path source, int colorsPaletteBitDepth) throws IOException {
    return extract(source, colorsPaletteBitDepth, false);
  }

  /**
   * Extracts metadata from an image, including dimensions, channel count, alpha presence, and
   * optionally a quantized color palette.
   *
   * @param colorsPaletteBitDepth bit depth for GIF-based quantization (e.g. 5 → 32 palette slots);
   *     pass 0 to skip palette extraction
   * @param debug if {@code true}, the response includes a {@code DebugInfo} object with the
   *     equivalent vips CLI command
   */
  public ExtractResult extract(Path source, int colorsPaletteBitDepth, boolean debug)
      throws IOException {
    return execute(
        worker ->
            (ExtractResult)
                worker.execute(
                    new Extract(source.toAbsolutePath().toString(), colorsPaletteBitDepth, debug)));
  }

  /** Create a thumbnail (width in pixels, height proportional). */
  public void thumbnail(Path source, Path target, int width) throws IOException {
    thumbnail(source, target, width, false);
  }

  /**
   * Create a thumbnail (width in pixels, height proportional).
   *
   * @param debug if {@code true}, the response includes a {@code DebugInfo} object with the
   *     equivalent vips CLI command
   */
  public void thumbnail(Path source, Path target, int width, boolean debug) throws IOException {
    execute(
        worker -> {
          worker.execute(
              new Thumbnail(
                  source.toAbsolutePath().toString(),
                  target.toAbsolutePath().toString(),
                  width,
                  debug));
          return null;
        });
  }

  /**
   * Apply a sequence of resize, border, and/or crop transformations to an image.
   *
   * <p>Steps are applied in order: resize → border → crop. One output file is written per requested
   * format, using {@code target} as the base path (without extension).
   *
   * @param source source image path
   * @param target base output path without file extension
   * @param resize exact target dimensions (width × height), or {@code null} to skip
   * @param border symmetric border to add, or {@code null} to skip
   * @param crop region to extract after other steps, or {@code null} to skip
   * @param background hex color for the border fill (e.g. "FFFFFF"), or {@code null} for white
   * @param formats output formats to write (e.g. JPG, WEBP, AVIF)
   */
  public void scaleTransform(
      Path source,
      Path target,
      ScaleTransform.ResizeStep resize,
      ScaleTransform.BorderStep border,
      ScaleTransform.CropStep crop,
      String background,
      List<OutputFormat> formats,
      Metadata metadata)
      throws IOException {
    scaleTransform(source, target, resize, border, crop, background, formats, metadata, false);
  }

  /**
   * Apply a sequence of resize, border, and/or crop transformations to an image.
   *
   * @param debug if {@code true}, the response includes a {@code DebugInfo} object with the
   *     equivalent vips CLI pipeline
   */
  public void scaleTransform(
      Path source,
      Path target,
      ScaleTransform.ResizeStep resize,
      ScaleTransform.BorderStep border,
      ScaleTransform.CropStep crop,
      String background,
      List<OutputFormat> formats,
      Metadata metadata,
      boolean debug)
      throws IOException {
    execute(
        worker -> {
          worker.execute(
              new ScaleTransform(
                  source.toAbsolutePath().toString(),
                  target.toAbsolutePath().toString(),
                  resize,
                  border,
                  crop,
                  background,
                  formats,
                  metadata,
                  debug));
          return null;
        });
  }

  /**
   * Generate multiple scaled outputs from a single source image in one worker call.
   *
   * <p>The source image is loaded only once, then all targets are produced from the in-memory base
   * image.
   *
   * @param source source image path
   * @param targets list of output targets; each target defines its own transform steps and formats
   */
  public void scaleTransformBatch(Path source, List<ScaleTransformBatch.BatchTarget> targets)
      throws IOException {
    scaleTransformBatch(source, targets, false);
  }

  /**
   * Generate multiple scaled outputs from a single source image in one worker call.
   *
   * @param debug if {@code true}, the response includes a {@code DebugInfo} object with the
   *     equivalent vips CLI pipeline for each batch target
   */
  public void scaleTransformBatch(
      Path source, List<ScaleTransformBatch.BatchTarget> targets, boolean debug)
      throws IOException {
    execute(
        worker -> {
          worker.execute(
              new ScaleTransformBatch(source.toAbsolutePath().toString(), targets, debug));
          return null;
        });
  }

  // ── AutoCloseable ─────────────────────────────────────────────

  /**
   * Shuts down all idle workers currently in the pool.
   *
   * <p>Workers that are currently borrowed by active threads are not affected. The typical {@code
   * try-with-resources} pattern with {@code parallelStream().forEach()} is safe because {@code
   * forEach()} blocks until all tasks complete before {@code close()} is entered.
   */
  @Override
  @SuppressWarnings("PMD.CloseResource")
  public void close() {
    List<WorkerBackend> remaining = new ArrayList<>(pool.size());
    pool.drainTo(remaining);
    for (WorkerBackend worker : remaining) {
      worker.close();
    }
  }
}
