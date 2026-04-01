package com.sitepark.vips.response;

import java.util.List;

public record VipsEnvironmentResponse(String vipsVersion, List<String> supportedFormats)
    implements Response {
  public VipsEnvironmentResponse {
    supportedFormats = List.copyOf(supportedFormats);
  }
}
