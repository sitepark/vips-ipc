package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record Resize(String source, String target, double scale, boolean debug)
    implements Command {}
