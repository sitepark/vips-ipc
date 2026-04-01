package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("PMD.AvoidCatchingThrowable")
public class GetEnvironmentHandler {

  private static final List<String> LOADER_NICKNAMES =
      List.of(
          "jpegload",
          "pngload",
          "webpload",
          "heifload",
          "tiffload",
          "gifload",
          "svgload",
          "pdfload");

  public VipsEnvironmentResponse handle(GetEnvironment cmd) {
    try {
      Vips.init();
    } catch (Throwable t) {
      return new VipsEnvironmentResponse(null, List.of());
    }
    try (var arena = Arena.ofConfined()) {
      String version = VipsHelper.version_string();
      List<String> formats = new ArrayList<>();
      for (String nick : LOADER_NICKNAMES) {
        if (VipsHelper.type_find(arena, "VipsForeignLoad", nick) != 0) {
          formats.add(nick);
        }
      }
      return new VipsEnvironmentResponse(version, List.copyOf(formats));
    }
  }
}
