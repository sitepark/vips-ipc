package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Result;
import com.sitepark.vips.command.Thumbnail;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ThumbnailHandler implements CommandHandler<Thumbnail> {

  @Override
  public Result handle(Thumbnail cmd) {
    Vips.init();
    Vips.run(
        arena -> {
          var image = VImage.newFromFile(arena, cmd.source());
          var metadata = new MetadataContext(SourceMetadata.capture(image), null);
          var thumb = image.autorot().thumbnailImage(cmd.width());
          try {
            Path parent = Path.of(cmd.target()).getParent();
            if (parent != null) {
              Files.createDirectories(parent);
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          MetadataPolicy.apply(thumb, metadata).writeToFile(cmd.target());
        });
    return null;
  }
}
