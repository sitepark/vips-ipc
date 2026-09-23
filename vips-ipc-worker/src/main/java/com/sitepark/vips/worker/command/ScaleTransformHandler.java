package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Result;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.worker.metadata.MetadataContext;
import com.sitepark.vips.worker.metadata.SourceMetadata;

public class ScaleTransformHandler implements CommandHandler<ScaleTransform> {

  @Override
  public Result handle(ScaleTransform cmd) {
    Vips.init();
    Vips.run(
        arena -> {
          var source = VImage.newFromFile(arena, cmd.source());
          var metadata = new MetadataContext(SourceMetadata.capture(source), cmd.metadata());
          // autorot bakes the EXIF orientation into the pixels. The orientation tag itself cannot
          // survive, because it only lives inside the EXIF block the whitelist drops.
          ScaleTransformSupport.applyAndWrite(
              source.autorot(),
              cmd.resize(),
              cmd.border(),
              cmd.crop(),
              cmd.background(),
              cmd.target(),
              cmd.formats(),
              metadata);
        });
    return null;
  }
}
