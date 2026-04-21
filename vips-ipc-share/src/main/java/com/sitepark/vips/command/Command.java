package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Config.class, name = "config"),
  @JsonSubTypes.Type(value = Extract.class, name = "extract"),
  @JsonSubTypes.Type(value = Resize.class, name = "resize"),
  @JsonSubTypes.Type(value = Thumbnail.class, name = "thumbnail"),
  @JsonSubTypes.Type(value = ScaleTransform.class, name = "scale-transform"),
  @JsonSubTypes.Type(value = ScaleTransformBatch.class, name = "scale-transform-batch"),
  @JsonSubTypes.Type(value = GetEnvironment.class, name = "get-environment"),
  @JsonSubTypes.Type(value = Shutdown.class, name = "shutdown")
})
public sealed interface Command
    permits Config,
        Extract,
        GetEnvironment,
        Resize,
        ScaleTransform,
        ScaleTransformBatch,
        Shutdown,
        Thumbnail {

  default boolean debug() {
    return false;
  }
}
