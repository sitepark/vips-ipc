package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Image format types supported by scale-transform commands. */
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public enum OutputFormatType {
  GIF("gif"),
  PNG("png"),
  JPG("jpg"),
  WEBP("webp"),
  AVIF("avif");

  private final String extension;

  OutputFormatType(String extension) {
    this.extension = extension;
  }

  @JsonValue
  public String extension() {
    return extension;
  }

  @JsonCreator
  public static OutputFormatType fromExtension(String extension) {
    for (OutputFormatType format : values()) {
      if (format.extension.equals(extension)) {
        return format;
      }
    }
    throw new IllegalArgumentException("Unknown output format: " + extension);
  }
}
