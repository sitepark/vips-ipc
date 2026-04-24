package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Result;
import com.sitepark.vips.command.ScaleTransform;

public class ScaleTransformHandler implements CommandHandler<ScaleTransform> {

  @Override
  public Result handle(ScaleTransform cmd) {
    Vips.init();
    Vips.run(
        arena -> {
          var base = VImage.newFromFile(arena, cmd.source());
          ScaleTransformSupport.applyAndWrite(
              base,
              cmd.resize(),
              cmd.border(),
              cmd.crop(),
              cmd.background(),
              cmd.target(),
              cmd.formats(),
              cmd.metadata());
        });
    return null;
  }
}
