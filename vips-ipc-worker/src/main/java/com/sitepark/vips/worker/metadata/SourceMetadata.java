package com.sitepark.vips.worker.metadata;

import app.photofox.vipsffm.VImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The whitelisted metadata captured from a source image, plus the ICC profile.
 *
 * <p>Captured before any processing, because the batch path decodes through {@code writeToMemory()}
 * / {@code newFromMemory()}, which strips the whole header.
 *
 * <p>Deliberately opaque: callers outside this package obtain one from {@link #capture(VImage)} and
 * hand it to a {@link MetadataContext} without inspecting it. Keeping the accessors package-private
 * is what allows {@link XmpTag} and the rest of the IPTC/XMP machinery to stay hidden.
 */
public final class SourceMetadata {

  private final Map<XmpTag, List<String>> xmpFields;
  private final byte[] iccProfileData;

  SourceMetadata(Map<XmpTag, List<String>> xmpFields, byte[] iccProfileData) {
    this.xmpFields = xmpFields;
    this.iccProfileData = iccProfileData == null ? new byte[0] : iccProfileData.clone();
  }

  /** An empty capture, used when no source metadata is available. */
  public static SourceMetadata empty() {
    return new SourceMetadata(new EnumMap<>(XmpTag.class), new byte[0]);
  }

  /** Reads the whitelisted XMP properties and the ICC profile from {@code source}. */
  public static SourceMetadata capture(VImage source) {
    return new SourceMetadata(
        XmpReader.parse(ImageMetadata.getBlob(source, ImageMetadata.XMP)),
        ImageMetadata.getBlob(source, ImageMetadata.ICC_PROFILE));
  }

  Map<XmpTag, List<String>> xmp() {
    return this.xmpFields;
  }

  byte[] iccProfile() {
    return this.iccProfileData.clone();
  }
}
