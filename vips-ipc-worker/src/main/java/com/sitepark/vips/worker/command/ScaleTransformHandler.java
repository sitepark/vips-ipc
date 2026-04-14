package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.worker.WorkerConfig;

public class ScaleTransformHandler implements CommandHandler<ScaleTransform> {

  private final WorkerConfig config;

  public ScaleTransformHandler(WorkerConfig config) {
    this.config = config;
  }

  @Override
  public void handle(ScaleTransform cmd) {
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
              config,
              cmd.metadata());
        });
  }
}
