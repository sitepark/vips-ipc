package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Path;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@SuppressFBWarnings("EI_EXPOSE_REP")
public record ScaleTransformBatch(String source, List<BatchTarget> targets, boolean debug)
    implements Command<Void> {

  public ScaleTransformBatch {
    if (targets != null) {
      targets = List.copyOf(targets);
    }
  }

  /**
   * One output target within a batch. Reuses the same step types as {@link ScaleTransform}.
   * Steps resize → border → crop are applied in sequence; null means skip.
   */
  public record BatchTarget(
      String target,
      ScaleTransform.ResizeStep resize,
      ScaleTransform.BorderStep border,
      ScaleTransform.CropStep crop,
      String background,
      List<OutputFormat> formats,
      Metadata metadata) {

    public BatchTarget {
      if (formats != null) {
        formats = List.copyOf(formats);
      }
    }
  }

  public static ScaleTransformBatch of(Path source, List<BatchTarget> targets) {
    return new ScaleTransformBatch(source.toAbsolutePath().toString(), targets, false);
  }
}
