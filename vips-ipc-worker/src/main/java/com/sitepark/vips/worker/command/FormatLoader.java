package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

public final class FormatLoader {

  private static final List<String> LOADER_FORMATS =
      List.of("jpeg", "png", "webp", "heif", "tiff", "gif", "svg", "pdf");

  private FormatLoader() {}

  public static List<String> supportedFormats() {
    Vips.init();
    try (var arena = Arena.ofConfined()) {
      List<String> formats = new ArrayList<>();
      for (String format : LOADER_FORMATS) {
        String nick = format + "load";
        if (VipsHelper.type_find(arena, "VipsForeignLoad", nick) != 0) {
          formats.add(format);
        }
      }
      return formats;
    }
  }
}
