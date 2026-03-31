package com.sitepark.vips.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OkResponse.class, name = "ok"),
  @JsonSubTypes.Type(value = ErrorResponse.class, name = "error")
})
public sealed interface Response permits OkResponse, ErrorResponse {}
