package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsExtend;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.CropStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import com.sitepark.vips.worker.WorkerConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared image transformation logic used by scale-transform handlers. */
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
      WorkerConfig config) {

    if (formats == null) {
      return;
    }

    var image = base;

    image = resize(image, resize);
    image = border(image, border, background);
    image = crop(image, crop);
    write(image, targetBase, formats, config);
  }

  private static VImage resize(VImage image, ResizeStep resize) {
    if (resize == null) {
      return image;
    }
    double hscale = (double) resize.width() / image.getWidth();
    double vscale = (double) resize.height() / image.getHeight();
    return image.resize(hscale, VipsOption.Double("vscale", vscale));
  }

  private static VImage border(VImage image, BorderStep border, String background) {
    if (border == null) {
      return image;
    }
    int newWidth = image.getWidth() + border.x() * 2;
    int newHeight = image.getHeight() + border.y() * 2;
    return image.embed(
        border.x(),
        border.y(),
        newWidth,
        newHeight,
        VipsOption.Enum("extend", VipsExtend.EXTEND_BACKGROUND),
        VipsOption.ArrayDouble("background", parseBackground(background)));
  }

  private static VImage crop(VImage image, CropStep crop) {
    if (crop == null) {
      return image;
    }
    return image.extractArea(crop.offsetX(), crop.offsetY(), crop.width(), crop.height());
  }

  private static void write(
      VImage image, String targetBase, List<OutputFormat> formats, WorkerConfig config) {
    for (OutputFormat format : formats) {
      write(image, targetBase, format, config);
    }
  }

  private static void write(
      VImage image, String targetBase, OutputFormat format, WorkerConfig config) {

    String path = targetBase + "." + format.extension();
    try {
      Path parent = Path.of(targetBase).getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    switch (format.type()) {
      case JPG ->
          image.writeToFile(
              path,
              VipsOption.Int("Q", format.effectiveQuality()),
              VipsOption.Boolean("interlace", config.jpegInterlace()),
              VipsOption.Boolean("strip", config.strip()));
      case WEBP ->
          image.writeToFile(
              path,
              VipsOption.Int("Q", format.effectiveQuality()),
              VipsOption.Boolean("strip", config.strip()));
      default -> image.writeToFile(path, VipsOption.Boolean("strip", config.strip()));
    }
  }

  /**
   * Parses a hex color string (e.g. "FFFFFF" or "FF0000") into an RGB list for libvips. Defaults to
   * white if the input is null or cannot be parsed.
   */
  static List<Double> parseBackground(String hex) {
    if (hex == null || hex.length() < 6) {
      return List.of(255.0, 255.0, 255.0);
    }
    try {
      double r = Integer.parseInt(hex.substring(0, 2), 16);
      double g = Integer.parseInt(hex.substring(2, 4), 16);
      double b = Integer.parseInt(hex.substring(4, 6), 16);
      return List.of(r, g, b);
    } catch (NumberFormatException e) {
      return List.of(255.0, 255.0, 255.0);
    }
  }
}
