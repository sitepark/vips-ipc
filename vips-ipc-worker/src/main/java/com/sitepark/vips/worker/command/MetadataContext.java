package com.sitepark.vips.worker.command;

import com.sitepark.vips.command.Metadata;

/**
 * What a write step needs to know about metadata: the whitelisted fields captured from the source
 * image, and the caller-supplied {@link Metadata} that overrides them.
 *
 * @param source whitelisted fields read off the source image; never {@code null}
 * @param explicit the caller's metadata, or {@code null} when the command carries none
 */
record MetadataContext(SourceMetadata source, Metadata explicit) {

  /** A context that carries no source metadata and no explicit override. */
  static MetadataContext empty() {
    return new MetadataContext(SourceMetadata.empty(), null);
  }
}
