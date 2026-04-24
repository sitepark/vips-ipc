package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsBandFormat;
import app.photofox.vipsffm.enums.VipsInterpretation;
import com.sitepark.vips.command.Compare;
import com.sitepark.vips.command.CompareResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CompareHandler implements CommandHandler<Compare> {

  private static final String[] CHANNEL_NAMES = {"red", "green", "blue", "alpha"};

  @Override
  public CompareResult handle(Compare cmd) {
    Vips.init();
    AtomicReference<CompareResult> ref = new AtomicReference<>();
    Vips.run(
        arena -> {
          var imageA = VImage.newFromFile(arena, cmd.imageA());
          var imageB = VImage.newFromFile(arena, cmd.imageB());

          int inputFormat = VipsHelper.image_get_format(imageA.getUnsafeStructAddress());
          double maxValue = VipsHelper.image_get_format_max(inputFormat);

          int aWidth = imageA.getWidth();
          int aHeight = imageA.getHeight();
          int bWidth = imageB.getWidth();
          int bHeight = imageB.getHeight();
          CompareResult.DimensionMismatch dimensionMismatch =
              (aWidth != bWidth || aHeight != bHeight)
                  ? new CompareResult.DimensionMismatch(aWidth, aHeight, bWidth, bHeight)
                  : null;

          var diff = imageA.subtract(imageB).abs();
          int bandCount = VipsHelper.image_get_bands(diff.getUnsafeStructAddress());

          List<CompareResult.ChannelDifference> channels = new ArrayList<>(bandCount);
          double totalMae = 0;

          for (int i = 0; i < bandCount; i++) {
            var band = diff.extractBand(i);
            double mae = band.avg();
            String name = i < CHANNEL_NAMES.length ? CHANNEL_NAMES[i] : "band-" + i;
            channels.add(new CompareResult.ChannelDifference(name, mae, mae / maxValue));
            totalMae += mae;
          }

          if (cmd.result() != null) {
            Compare.Color color =
                cmd.highlightColor() != null
                    ? cmd.highlightColor()
                    : Compare.DEFAULT_HIGHLIGHT_COLOR;
            writeDiffImage(arena, imageA, imageB, bandCount, cmd.result(), color);
          }

          double avgMae = totalMae / bandCount;
          ref.set(new CompareResult(channels, avgMae, avgMae / maxValue, dimensionMismatch));
        });
    return ref.get();
  }

  private void writeDiffImage(
      Arena arena,
      VImage imageA,
      VImage imageB,
      int bandCount,
      String resultPath,
      Compare.Color color) {
    try {
      Path parent = Path.of(resultPath).getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    boolean hasAlpha = VipsHelper.image_hasalpha(imageA.getUnsafeStructAddress());
    VImage aFull = imageA.cast(VipsBandFormat.FORMAT_UCHAR);
    VImage bFull = imageB.cast(VipsBandFormat.FORMAT_UCHAR);

    int width = aFull.getWidth();
    int height = aFull.getHeight();
    int bWidth = bFull.getWidth();
    int bHeight = bFull.getHeight();

    byte[] aPixels = aFull.writeToMemory().toArray(ValueLayout.JAVA_BYTE);
    byte[] bPixels = bFull.writeToMemory().toArray(ValueLayout.JAVA_BYTE);

    byte[] highlightPixel = buildHighlightPixel(bandCount, hasAlpha, color);
    byte[] result =
        buildDiffPixels(
            aPixels, width, height, bPixels, bWidth, bHeight, bandCount, hasAlpha, highlightPixel);

    VImage.newFromMemory(
            arena,
            arena.allocateFrom(ValueLayout.JAVA_BYTE, result),
            width,
            height,
            bandCount,
            VipsBandFormat.FORMAT_UCHAR.getRawValue())
        .copy(VipsOption.Enum("interpretation", VipsInterpretation.INTERPRETATION_sRGB))
        .writeToFile(resultPath);
  }

  private static byte[] buildHighlightPixel(int bandCount, boolean hasAlpha, Compare.Color color) {
    byte[] pixel = new byte[bandCount];
    int[] components = {color.r(), color.g(), color.b()};
    int rgbBandCount = hasAlpha ? bandCount - 1 : bandCount;
    for (int i = 0; i < Math.min(rgbBandCount, components.length); i++) {
      pixel[i] = (byte) components[i];
    }
    if (hasAlpha) {
      pixel[bandCount - 1] = (byte) 255;
    }
    return pixel;
  }

  private static byte[] buildDiffPixels(
      byte[] aPixels,
      int aWidth,
      int aHeight,
      byte[] bPixels,
      int bWidth,
      int bHeight,
      int bandCount,
      boolean hasAlpha,
      byte[] highlightPixel) {
    byte[] result = new byte[aWidth * aHeight * bandCount];
    int alphaIdx = bandCount - 1;
    for (int y = 0; y < aHeight; y++) {
      for (int x = 0; x < aWidth; x++) {
        int aOffset = (y * aWidth + x) * bandCount;
        int resultOffset = aOffset;
        if (x >= bWidth || y >= bHeight) {
          System.arraycopy(highlightPixel, 0, result, resultOffset, bandCount);
        } else {
          int bOffset = (y * bWidth + x) * bandCount;
          if (isDifferentPixel(aPixels, aOffset, bPixels, bOffset, bandCount, hasAlpha, alphaIdx)) {
            System.arraycopy(highlightPixel, 0, result, resultOffset, bandCount);
          } else {
            System.arraycopy(aPixels, aOffset, result, resultOffset, bandCount);
          }
        }
      }
    }
    return result;
  }

  private static boolean isDifferentPixel(
      byte[] aPixels,
      int aOffset,
      byte[] bPixels,
      int bOffset,
      int bandCount,
      boolean hasAlpha,
      int alphaIdx) {
    // Two fully-transparent pixels are visually identical regardless of their RGB values.
    boolean aTransparent = hasAlpha && (aPixels[aOffset + alphaIdx] & 0xFF) == 0;
    boolean bTransparent = hasAlpha && (bPixels[bOffset + alphaIdx] & 0xFF) == 0;
    if (aTransparent && bTransparent) {
      return false;
    }
    for (int b = 0; b < bandCount; b++) {
      if (aPixels[aOffset + b] != bPixels[bOffset + b]) {
        return true;
      }
    }
    return false;
  }
}
