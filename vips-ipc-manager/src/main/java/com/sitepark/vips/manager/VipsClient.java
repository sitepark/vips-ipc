package com.sitepark.vips.manager;

import com.sitepark.vips.command.Config;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.Thumbnail;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("PMD.TooManyMethods")
public class VipsClient implements AutoCloseable {

  private final WorkerProcess workerProcess;

  public static VipsClientBuilder builder() {
    return new VipsClientBuilder();
  }

  VipsClient(WorkerProcess workerProcess) {
    this.workerProcess = workerProcess;
  }

  /**
   * Returns information about the libvips installation on this system, including the version and a
   * list of available image format loaders. Use this to verify prerequisites before processing
   * images.
   */
  public VipsEnvironmentResponse getEnvironment() throws IOException {
    return workerProcess.queryEnvironment();
  }

  /**
   * Sets global encoding parameters for all subsequent image write operations.
   *
   * <p>Only non-null fields are applied; omit a parameter (pass {@code null}) to keep the current
   * value. Defaults: jpegInterlace = true (progressive JPEG), strip = false.
   *
   * @param jpegInterlace {@code true} for progressive JPEG, {@code false} for baseline, or
   *     {@code null} to keep current
   * @param strip {@code true} to strip all metadata (EXIF, IPTC, XMP), or {@code null} to keep
   *     current
   */
  public void configure(Boolean jpegInterlace, Boolean strip) throws IOException {
    workerProcess.execute(new Config(jpegInterlace, strip));
  }

  /**
   * Scale an image by factor (0.5 = 50%).
   */
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
    workerProcess.execute(
        new Resize(
            source.toAbsolutePath().toString(), target.toAbsolutePath().toString(), scale, debug));
  }

  /**
   * Create a thumbnail (width in pixels, height proportional).
   */
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
    workerProcess.execute(
        new Thumbnail(
            source.toAbsolutePath().toString(), target.toAbsolutePath().toString(), width, debug));
  }

  /**
   * Apply a sequence of resize, border, and/or crop transformations to an image.
   *
   * <p>All parameters except {@code source}, {@code target} and {@code formats} are optional (pass
   * {@code null} to skip a step). Steps are applied in order: resize → border → crop. One output
   * file is written per requested format, using {@code target} as the base path (without
   * extension).
   *
   * @param source source image path
   * @param target base output path without file extension
   * @param resize exact target dimensions (width × height, ignoring aspect ratio), or {@code null}
   * @param border symmetric border to add (x pixels left+right, y pixels top+bottom), or
   *     {@code null}
   * @param crop region to extract after other steps, or {@code null}
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
      List<OutputFormat> formats)
      throws IOException {
    scaleTransform(source, target, resize, border, crop, background, formats, false);
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
      boolean debug)
      throws IOException {
    workerProcess.execute(
        new ScaleTransform(
            source.toAbsolutePath().toString(),
            target.toAbsolutePath().toString(),
            resize,
            border,
            crop,
            background,
            formats,
            debug));
  }

  /**
   * Generate multiple scaled outputs from a single source image in one worker call.
   *
   * <p>The source image is loaded only once using shrink-on-load (equivalent to the BoxGroup
   * optimization), then all targets are produced from the in-memory base image.
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
    workerProcess.execute(
        new ScaleTransformBatch(source.toAbsolutePath().toString(), targets, debug));
  }

  @Override
  public void close() {
    workerProcess.close();
  }
}
