package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "result")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CompareResult.class, name = "compare"),
  @JsonSubTypes.Type(value = ExtractResult.class, name = "extract"),
})
public sealed interface Result permits CompareResult, ExtractResult {}
