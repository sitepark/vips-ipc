package com.sitepark.vips.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sitepark.vips.command.Result;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OkResponse(Result result, DebugInfo debug) implements Response {}
