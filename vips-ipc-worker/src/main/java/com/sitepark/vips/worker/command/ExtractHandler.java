package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VBlob;
import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsBandFormat;
import app.photofox.vipsffm.enums.VipsInterpretation;
import app.photofox.vipsffm.enums.VipsSize;
import com.sitepark.vips.command.*;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ExtractHandler implements CommandHandler<Extract> {

  private static final int THUMB_SIZE = 100;

  @Override
  public ExtractResult handle(Extract cmd) {
    Vips.init();

    AtomicReference<ExtractResult> ref = new AtomicReference<>();
    Vips.run(
        arena -> {
          var image = VImage.newFromFile(arena, cmd.source());

          int width = image.getWidth();
          int height = image.getHeight();
          int channels = VipsHelper.image_get_bands(image.getUnsafeStructAddress());
          boolean hasAlpha = image.hasAlpha();

          ColorPalette colorPalette =
              buildPaletteIfRequested(arena, image, cmd.source(), cmd.colorsPaletteBitDepth());

          ref.set(new ExtractResult(width, height, channels, hasAlpha, colorPalette));
        });
    return ref.get();
  }

  private ColorPalette buildPaletteIfRequested(
      Arena arena, VImage image, String source, int bitDepth) {
    if (bitDepth <= 0) {
      return null;
    }
    boolean needsThumb = image.getWidth() > THUMB_SIZE || image.getHeight() > THUMB_SIZE;
    VImage thumb =
        needsThumb
            ? VImage.thumbnail(
                arena,
                source,
                THUMB_SIZE,
                VipsOption.Int("height", THUMB_SIZE),
                VipsOption.Enum("size", VipsSize.SIZE_DOWN))
            : image;
    return calculateColorPalette(arena, thumb, bitDepth);
  }

  public ColorPalette calculateColorPalette(Arena arena, VImage thumb, int bitDepth) {

    VImage colorImg = thumb.colourspace(VipsInterpretation.INTERPRETATION_sRGB);
    VImage flatImg = colorImg.hasAlpha() ? colorImg.flatten() : colorImg;

    // quantize via GIF round-trip: libimagequant picks the best palette
    // VBlob gifData = flatImg.gifsaveBuffer(VipsOption.Int("bitdepth", bitDepth));
    VBlob gifData =
        flatImg.magicksaveBuffer(
            VipsOption.Int("bitdepth", bitDepth), VipsOption.String("format", "gif"));
    // VImage loaded = VImage.gifloadBuffer(arena, gifData);
    VImage loaded = VImage.magickloadBuffer(arena, gifData);
    // GIF load produces RGBA; flatten composites alpha against white → 3-band RGB
    VImage rgbImage =
        (loaded.hasAlpha() ? loaded.flatten() : loaded).cast(VipsBandFormat.FORMAT_UCHAR);

    int w = rgbImage.getWidth();
    int h = rgbImage.getHeight();
    byte[] pixels = rgbImage.writeToMemory().toArray(ValueLayout.JAVA_BYTE);
    int stride = pixels.length / h;
    return buildColorPalette(pixels, w, h, stride);
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private ColorPalette buildColorPalette(byte[] pixels, int w, int h, int stride) {
    Map<Integer, Long> counts = new HashMap<>();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int offset = y * stride + x * 3;
        int r = Byte.toUnsignedInt(pixels[offset]);
        int g = Byte.toUnsignedInt(pixels[offset + 1]);
        int b = Byte.toUnsignedInt(pixels[offset + 2]);
        counts.merge((r << 16) | (g << 8) | b, 1L, Long::sum);
      }
    }

    int pixelCount = w * h;
    List<ColorPaletteEntry> entries = new ArrayList<>(counts.size());
    for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
      int key = entry.getKey();
      long count = entry.getValue();
      entries.add(
          new ColorPaletteEntry(
              (key >> 16) & 0xFF,
              (key >> 8) & 0xFF,
              key & 0xFF,
              count,
              (double) count / pixelCount * 100.0));
    }
    entries.sort(Comparator.comparingLong(ColorPaletteEntry::pixelCount).reversed());

    return new ColorPalette(entries);
  }
}
