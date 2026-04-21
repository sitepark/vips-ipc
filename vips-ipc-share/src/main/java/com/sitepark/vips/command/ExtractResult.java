package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record ExtractResult(
    int width, int height, int channels, boolean hasAlpha, ColorPalette colorPalette)
    implements Result {}
