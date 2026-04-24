package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.file.Path;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record Resize(String source, String target, double scale, boolean debug)
    implements Command<Void> {
  public static Resize of(Path source, String target, double scale) {
    return new Resize(source.toString(), target, scale, false);
  }

  public static Resize of(Path source, Path target, double scale) {
    return new Resize(
        source.toAbsolutePath().toString(), target.toAbsolutePath().toString(), scale, false);
  }
}
