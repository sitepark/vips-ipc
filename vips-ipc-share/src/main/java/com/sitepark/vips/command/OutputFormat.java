package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Output format for a scale-transform operation, combining the image format with an optional
 * per-format quality setting.
 *
 * <p>If {@code quality} is {@code null}, a default of 82 is used for formats that support it
 * (JPG, WebP).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutputFormat(OutputFormatType type, Integer quality) {

  private static final int DEFAULT_QUALITY = 82;

  /** Returns the quality to use when writing this format. Defaults to 82 if not set. */
  public int effectiveQuality() {
    return quality != null ? quality : DEFAULT_QUALITY;
  }

  /** Returns the file extension for this format (e.g. {@code "jpg"}). */
  public String extension() {
    return type.extension();
  }

  /** Creates an {@link OutputFormat} with the given type and no explicit quality (default: 82). */
  public static OutputFormat of(OutputFormatType type) {
    return new OutputFormat(type, null);
  }

  /** Creates an {@link OutputFormat} with the given type and an explicit quality value. */
  public static OutputFormat of(OutputFormatType type, int quality) {
    return new OutputFormat(type, quality);
  }
}
