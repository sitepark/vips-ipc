package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.file.Path;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record Thumbnail(String source, String target, int width, boolean debug)
    implements Command<Void> {

  public static Thumbnail of(Path source, Path target, int width) {
    return new Thumbnail(
        source.toAbsolutePath().toString(), target.toAbsolutePath().toString(), width, false);
  }
}
