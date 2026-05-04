package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsBlendMode;
import app.photofox.vipsffm.enums.VipsExtend;
import app.photofox.vipsffm.enums.VipsForeignHeifCompression;
import com.sitepark.vips.command.Metadata;
import com.sitepark.vips.command.OutputFormat;
import com.sitepark.vips.command.ScaleTransform.BorderStep;
import com.sitepark.vips.command.ScaleTransform.CropStep;
import com.sitepark.vips.command.ScaleTransform.ResizeStep;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/** Shared image transformation logic used by scale-transform handlers. */
final class ScaleTransformSupport {

  private static final String STRIP = "strip";
  private static final String BACKGROUND = "background";

  private ScaleTransformSupport() {}

  /**
   * Applies resize → border → crop in sequence and writes one output file per requested format.
   * {@code targetBase} is the base file path; the format's extension is appended unless {@link
   * OutputFormat#appendExtension()} returns {@code false}.
   */
  static void applyAndWrite(
      VImage base,
      ResizeStep resize,
      BorderStep border,
      CropStep crop,
      String background,
      String targetBase,
      List<OutputFormat> formats,
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
    try {
      write(image, targetBase, formats, backgroundRgba, metadata);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
        VipsOption.ArrayDouble(BACKGROUND, Collections.nCopies(bands, 0.0)));
  }

  private static VImage crop(VImage image, CropStep crop) {
    if (crop == null) {
      return image;
    }
    int srcX = Math.max(0, crop.offsetX());
    int srcY = Math.max(0, crop.offsetY());
    int overlapWidth = clampCropDimension(crop.width(), image.getWidth(), crop.offsetX());
    int overlapHeight = clampCropDimension(crop.height(), image.getHeight(), crop.offsetY());
    if (overlapWidth <= 0 || overlapHeight <= 0) {
      return image;
    }
    VImage extracted = image.extractArea(srcX, srcY, overlapWidth, overlapHeight);
    if (overlapWidth == crop.width() && overlapHeight == crop.height()) {
      return extracted;
    }
    // The crop region extends outside the image — embed the extracted portion into a canvas
    // of the full crop size. Out-of-bounds areas get transparent fill here; the actual
    // background color is applied during the write phase (flatten/composite).
    int embedX = Math.max(0, -crop.offsetX());
    int embedY = Math.max(0, -crop.offsetY());
    int bands = VipsHelper.image_get_bands(extracted.getUnsafeStructAddress());
    return extracted.embed(
        embedX,
        embedY,
        crop.width(),
        crop.height(),
        VipsOption.Enum("extend", VipsExtend.EXTEND_BACKGROUND),
        VipsOption.ArrayDouble(BACKGROUND, Collections.nCopies(bands, 0.0)));
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
   * @param offset the crop offset (left or top) in the same axis; may be negative
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
      List<Double> backgroundRgba,
      Metadata metadata)
      throws IOException {
    for (OutputFormat format : formats) {
      write(image, targetBase, format, backgroundRgba, metadata);
    }
  }

  private static String preparePath(String targetBase, OutputFormat format) {
    String path = targetBase + "." + format.extension();
    try {
      Path parent = Path.of(path).getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return path;
  }

  private static String resultPath(String targetBase, OutputFormat format) {
    return format.appendExtension() ? targetBase + "." + format.extension() : targetBase;
  }

  private static void write(
      VImage image,
      String targetBase,
      OutputFormat format,
      List<Double> backgroundRgba,
      Metadata metadata)
      throws IOException {

    String path = preparePath(targetBase, format);
    String resultPath = resultPath(targetBase, format);
    List<Double> backgroundRgb = backgroundRgba.subList(0, 3);
    switch (format) {
      case OutputFormat.JpegFormat jpg ->
          IptcBuilder.applyToImage(
                  image.flatten(VipsOption.ArrayDouble(BACKGROUND, backgroundRgb)), metadata)
              .writeToFile(
                  path,
                  VipsOption.Int("Q", jpg.quality()),
                  VipsOption.Boolean("interlace", jpg.interlace()),
                  VipsOption.Boolean(STRIP, jpg.strip()));
      case OutputFormat.WebpFormat webp ->
          IptcBuilder.applyToImage(
                  image.flatten(VipsOption.ArrayDouble(BACKGROUND, backgroundRgb)), metadata)
              .writeToFile(
                  path,
                  VipsOption.Int("Q", webp.quality()),
                  VipsOption.Boolean("lossless", webp.lossless()),
                  VipsOption.Boolean(STRIP, webp.strip()));
      case OutputFormat.PngFormat png ->
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
              .writeToFile(path, VipsOption.Boolean(STRIP, png.strip()));
      case OutputFormat.GifFormat gif -> {
        IptcBuilder.applyToImage(
                image
                    .linear(List.of(0.0, 0.0, 0.0, 0.0), backgroundRgba)
                    .composite2(image, VipsBlendMode.BLEND_MODE_OVER),
                metadata)
            .writeToFile(path, VipsOption.Boolean(STRIP, gif.strip()));
      }
      case OutputFormat.AvifFormat avif ->
          IptcBuilder.applyToImage(
                  image
                      .linear(List.of(0.0, 0.0, 0.0, 0.0), backgroundRgba)
                      .composite2(image, VipsBlendMode.BLEND_MODE_OVER),
                  metadata)
              .writeToFile(
                  path,
                  VipsOption.Enum(
                      "compression", VipsForeignHeifCompression.FOREIGN_HEIF_COMPRESSION_AV1),
                  VipsOption.Int("Q", avif.quality()),
                  VipsOption.Boolean("lossless", avif.lossless()),
                  VipsOption.Boolean(STRIP, avif.strip()));
    }

    if (!path.equals(resultPath)) {
      Files.move(Path.of(path), Path.of(resultPath), StandardCopyOption.REPLACE_EXISTING);
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
