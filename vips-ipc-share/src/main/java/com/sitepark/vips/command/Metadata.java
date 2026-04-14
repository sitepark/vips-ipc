package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Metadata to embed in output images (IPTC). Null fields are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Metadata(String copyright, String title, String description) {}
