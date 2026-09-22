package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The whitelisted metadata captured from a source image, plus the ICC profile.
 *
 * <p>Captured before any processing, because the batch path decodes through {@code writeToMemory()}
 * / {@code newFromMemory()}, which strips the whole header.
 */
record SourceMetadata(Map<XmpTag, List<String>> xmp, byte[] iccProfile) {

  SourceMetadata {
    iccProfile = iccProfile == null ? new byte[0] : iccProfile.clone();
  }

  @Override
  public byte[] iccProfile() {
    return this.iccProfile.clone();
  }

  /** An empty capture, used when no source metadata is available. */
  static SourceMetadata empty() {
    return new SourceMetadata(new EnumMap<>(XmpTag.class), new byte[0]);
  }

  /** Reads the whitelisted XMP properties and the ICC profile from {@code source}. */
  static SourceMetadata capture(VImage source) {
    return new SourceMetadata(
        XmpReader.parse(ImageMetadata.getBlob(source, ImageMetadata.XMP)),
        ImageMetadata.getBlob(source, ImageMetadata.ICC_PROFILE));
  }
}
