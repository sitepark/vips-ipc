package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsHelper;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.util.List;

@SuppressWarnings("PMD.AvoidCatchingThrowable")
public class GetEnvironmentHandler {

  public VipsEnvironmentResponse handle(GetEnvironment cmd) {
    try {
      Vips.init();
    } catch (Throwable t) {
      return new VipsEnvironmentResponse(null, List.of());
    }
    List<String> formats = FormatLoader.supportedFormats();
    String version = VipsHelper.version_string();
    return new VipsEnvironmentResponse(version, formats);
  }
}
