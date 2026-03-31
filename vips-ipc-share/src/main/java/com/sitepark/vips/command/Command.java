package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Resize.class, name = "resize"),
  @JsonSubTypes.Type(value = Thumbnail.class, name = "thumbnail"),
  @JsonSubTypes.Type(value = Shutdown.class, name = "shutdown")
})
public sealed interface Command permits Resize, Thumbnail, Shutdown {}
