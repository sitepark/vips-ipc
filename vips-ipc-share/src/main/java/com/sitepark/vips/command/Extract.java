package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.file.Path;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
/**
 * @param colorsPaletteBitDepth bitdepth=5: 2^5=32 palette slots used by libimagequant for
 *     quantization
 */
public record Extract(String source, int colorsPaletteBitDepth, boolean debug)
    implements Command<ExtractResult> {

  public static Extract of(Path source, int colorsPaletteBitDepth) {
    return new Extract(source.toString(), colorsPaletteBitDepth, false);
  }
}
