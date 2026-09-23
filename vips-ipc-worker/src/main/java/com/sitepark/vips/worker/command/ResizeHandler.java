package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.Vips;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.Result;
import com.sitepark.vips.worker.metadata.MetadataContext;
import com.sitepark.vips.worker.metadata.MetadataPolicy;
import com.sitepark.vips.worker.metadata.SourceMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResizeHandler implements CommandHandler<Resize> {

  @Override
  public Result handle(Resize cmd) {
    Vips.init();
    Vips.run(
        arena -> {
          var image = VImage.newFromFile(arena, cmd.source());
          var metadata = new MetadataContext(SourceMetadata.capture(image), null);
          var scaled = image.autorot().resize(cmd.scale());
          try {
            Path parent = Path.of(cmd.target()).getParent();
            if (parent != null) {
              Files.createDirectories(parent);
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          MetadataPolicy.apply(scaled, metadata).writeToFile(cmd.target());
        });
    return null;
  }
}
