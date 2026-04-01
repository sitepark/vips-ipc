package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Sets global output encoding parameters for all subsequent image write operations. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Config(
    Boolean jpegInterlace, // Write progressive JPEG; null = keep current
    Boolean strip // Strip all metadata (EXIF, IPTC, XMP); null = keep current
    ) implements Command {}
