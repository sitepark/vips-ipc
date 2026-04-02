package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@SuppressFBWarnings("EI_EXPOSE_REP")
public record ScaleTransform(
    String source,
    String target,
    ResizeStep resize,
    BorderStep border,
    CropStep crop,
    String background,
    List<OutputFormat> formats,
    boolean debug)
    implements Command {

  public ScaleTransform {
    if (formats != null) {
      formats = List.copyOf(formats);
    }
  }

  /** Resize to exact width × height, ignoring aspect ratio. */
  public record ResizeStep(int width, int height) {}

  /** Add symmetric border: x pixels left+right, y pixels top+bottom. */
  public record BorderStep(int x, int y) {}

  /** Crop a region at the given offset. */
  public record CropStep(int width, int height, int offsetX, int offsetY) {}
}
