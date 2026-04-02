package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record Thumbnail(String source, String target, int width, boolean debug)
    implements Command {}
