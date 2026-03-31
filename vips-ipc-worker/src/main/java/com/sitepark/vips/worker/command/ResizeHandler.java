package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Resize;

public class ResizeHandler implements CommandHandler<Resize> {

  @Override
  public void handle(Resize cmd) {
    Vips.run(
        arena -> {
          var image = VImage.newFromFile(arena, cmd.source());
          var scaled = image.resize(cmd.scale());
          scaled.writeToFile(cmd.target());
        });
  }
}
