package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsBlendMode;
import app.photofox.vipsffm.enums.VipsExtend;
import com.sitepark.vips.command.Metadata;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.CropStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.worker.WorkerConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Shared image transformation logic used by scale-transform handlers.
 */
final class ScaleTransformSupport {

  private ScaleTransformSupport() {}

  /**
   * Applies resize → border → crop in sequence and writes one output file per requested format.
   * {@code targetBase} is the file path without extension; the format's extension is appended.
   */
  static void applyAndWrite(
      VImage base,
      ResizeStep resize,
      BorderStep border,
      CropStep crop,
      String background,
      String targetBase,
      List<OutputFormat> formats,
      WorkerConfig config,
      Metadata metadata) {

    if (formats == null) {
      return;
    }

    List<Double> backgroundRgba = parseBackground(background);

    var image = base;

    if (!VipsHelper.image_hasalpha(image.getUnsafeStructAddress())) {
      image = image.bandjoinConst(List.of(255.0));
    }

    image = resize(image, resize);
    image = border(image, border);
    image = crop(image, crop);
    write(image, targetBase, formats, config, backgroundRgba, metadata);
  }

  private static VImage resize(VImage image, ResizeStep resize) {
    if (resize == null) {
      return image;
    }
    double hscale = (double) resize.width() / image.getWidth();
    double vscale = (double) resize.height() / image.getHeight();
    return image.resize(hscale, VipsOption.Double("vscale", vscale));
  }

  private static VImage border(VImage image, BorderStep border) {
    if (border == null) {
      return image;
    }
    int newWidth = image.getWidth() + border.x() * 2;
    int newHeight = image.getHeight() + border.y() * 2;

    // Use actual band count to size the background vector correctly — bands is the authoritative
    // source for the vector length embed expects.
    int bands = VipsHelper.image_get_bands(image.getUnsafeStructAddress());

    return image.embed(
        border.x(),
        border.y(),
        newWidth,
        newHeight,
        VipsOption.Enum("extend", VipsExtend.EXTEND_BACKGROUND),
        VipsOption.ArrayDouble("background", Collections.nCopies(bands, 0.0)));
  }

  private static VImage crop(VImage image, CropStep crop) {
    if (crop == null) {
      return image;
    }
    int offsetX = Math.max(0, crop.offsetX());
    int offsetY = Math.max(0, crop.offsetY());
    int width = clampCropDimension(crop.width(), image.getWidth(), crop.offsetX());
    int height = clampCropDimension(crop.height(), image.getHeight(), crop.offsetY());
    if (width <= 0 || height <= 0) {
      return image;
    }
    return image.extractArea(offsetX, offsetY, width, height);
  }

  /**
   * Clamps a requested crop dimension to the available image space.
   *
   * <p>Handles both positive and negative offsets:
   *
   * <ul>
   *   <li>A negative offset means the crop starts before the image origin; the requested dimension
   *       is reduced by the out-of-bounds portion.
   *   <li>A positive offset shifts the start into the image; the dimension is clamped so the region
   *       does not exceed the image boundary.
   * </ul>
   *
   * <p>A return value ≤ 0 means nothing can be extracted and the caller should skip the crop.
   *
   * @param requested the desired crop width or height
   * @param imageSize the actual image width or height after all previous operations
   * @param offset    the crop offset (left or top) in the same axis; may be negative
   * @return the effective dimension to pass to {@code extractArea}, clamped to available space
   */
  static int clampCropDimension(int requested, int imageSize, int offset) {
    int adjusted = requested + Math.min(0, offset);
    return Math.min(adjusted, imageSize - Math.max(0, offset));
  }

  private static void write(
      VImage image,
      String targetBase,
      List<OutputFormat> formats,
      WorkerConfig config,
      List<Double> backgroundRgba,
      Metadata metadata) {
    for (OutputFormat format : formats) {
      write(image, targetBase, format, config, backgroundRgba, metadata);
    }
  }

  private static void write(
      VImage image,
      String targetBase,
      OutputFormat format,
      WorkerConfig config,
      List<Double> backgroundRgba,
      Metadata metadata) {

    String path = targetBase + "." + format.extension();
    try {
      Path parent = Path.of(targetBase).getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    List<Double> backgroundRgb = backgroundRgba.subList(0, 3);

    switch (format.type()) {
      case JPG ->
          IptcBuilder.applyToImage(
                  image.flatten(VipsOption.ArrayDouble("background", backgroundRgb)), metadata)
              .writeToFile(
                  path,
                  VipsOption.Int("Q", format.effectiveQuality()),
                  VipsOption.Boolean("interlace", config.jpegInterlace()),
                  VipsOption.Boolean("strip", config.strip()));
      case WEBP ->
          IptcBuilder.applyToImage(
                  image.flatten(VipsOption.ArrayDouble("background", backgroundRgb)), metadata)
              .writeToFile(
                  path,
                  VipsOption.Int("Q", format.effectiveQuality()),
                  VipsOption.Boolean("strip", config.strip()));
      default ->
          // Composite the source (with its alpha) over a constant background image. This gives:
          // - transparent/border pixels → (bg_rgb, bg_alpha)
          // - opaque source pixels     → (src_rgb, 255)
          // The border fill (transparent black from embed) produces the same result as image
          // transparent pixels — both blend to bg_rgba via Porter-Duff OVER.
          IptcBuilder.applyToImage(
                  image
                      .linear(List.of(0.0, 0.0, 0.0, 0.0), backgroundRgba)
                      .composite2(image, VipsBlendMode.BLEND_MODE_OVER),
                  metadata)
              .writeToFile(path, VipsOption.Boolean("strip", config.strip()));
    }
  }

  /**
   * Parses a hex color string into an RGBA list for libvips. Supports 6-digit hex (RRGGBB) and
   * 8-digit hex (RRGGBBAA). When no alpha digit is present in the hex string, alpha defaults to 0
   * (fully transparent). Defaults to transparent white if the input is null or cannot be parsed.
   */
  static List<Double> parseBackground(String hex) {
    if (hex == null || hex.length() < 6) {
      return List.of(255.0, 255.0, 255.0, 0.0);
    }
    try {
      double r = Integer.parseInt(hex.substring(0, 2), 16);
      double g = Integer.parseInt(hex.substring(2, 4), 16);
      double b = Integer.parseInt(hex.substring(4, 6), 16);
      double a = hex.length() >= 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 0;
      return List.of(r, g, b, a);
    } catch (NumberFormatException e) {
      return List.of(255.0, 255.0, 255.0, 0.0);
    }
  }
}
