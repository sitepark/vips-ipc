package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Thumbnail;

public class ThumbnailHandler implements CommandHandler<Thumbnail> {

  @Override
  public void handle(Thumbnail cmd) {
    Vips.run(
        arena -> {
          var image = VImage.newFromFile(arena, cmd.source());
          var thumb = image.thumbnailImage(cmd.width());
          thumb.writeToFile(cmd.target());
        });
  }
}
