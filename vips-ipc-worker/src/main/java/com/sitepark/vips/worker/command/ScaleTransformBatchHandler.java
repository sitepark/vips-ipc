package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsSize;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.ScaleTransformBatch.BatchTarget;
import com.sitepark.vips.worker.WorkerConfig;
import java.lang.foreign.MemorySegment;

public class ScaleTransformBatchHandler implements CommandHandler<ScaleTransformBatch> {

  private final WorkerConfig config;

  public ScaleTransformBatchHandler(WorkerConfig config) {
    this.config = config;
  }

  @Override
  public void handle(ScaleTransformBatch cmd) {
    Vips.init();
    Vips.run(
        arena -> {
          // Find the largest resize target to determine the optimal thumbnail size.
          int maxWidth =
              cmd.targets().stream()
                  .filter(t -> t.resize() != null)
                  .mapToInt(t -> t.resize().width())
                  .max()
                  .orElse(0);
          int maxHeight =
              cmd.targets().stream()
                  .filter(t -> t.resize() != null)
                  .mapToInt(t -> t.resize().height())
                  .max()
                  .orElse(0);

          // Load with thumbnail() for JPEG/WebP shrink-on-load efficiency, then
          // materialize to a random-access in-memory image. thumbnail() uses sequential
          // access which is exhausted after the first writeToFile(); writeToMemory()
          // forces full evaluation and newFromMemory() provides random access for reuse.
          // xres/yres are copied via copy() because newFromMemory() strips all metadata.
          // If no resize steps are present, load the full image directly.
          VImage base;
          if (maxWidth > 0 && maxHeight > 0) {
            VImage thumb =
                VImage.thumbnail(
                    arena,
                    cmd.source(),
                    maxWidth,
                    VipsOption.Int("height", maxHeight),
                    VipsOption.Enum("size", VipsSize.SIZE_FORCE));
            MemorySegment pixels = thumb.writeToMemory();
            int width = thumb.getWidth();
            int height = thumb.getHeight();
            int bands = VipsHelper.image_get_bands(thumb.getUnsafeStructAddress());
            int format = VipsHelper.image_get_format(thumb.getUnsafeStructAddress());
            double xres = VipsHelper.image_get_xres(thumb.getUnsafeStructAddress());
            double yres = VipsHelper.image_get_yres(thumb.getUnsafeStructAddress());
            base = VImage.newFromMemory(arena, pixels, width, height, bands, format);
            base = base.copy(VipsOption.Double("xres", xres), VipsOption.Double("yres", yres));
          } else {
            base = VImage.newFromFile(arena, cmd.source());
          }

          for (BatchTarget target : cmd.targets()) {
            ScaleTransformSupport.applyAndWrite(
                base,
                target.resize(),
                target.border(),
                target.crop(),
                target.background(),
                target.target(),
                target.formats(),
                config);
          }
        });
  }
}
