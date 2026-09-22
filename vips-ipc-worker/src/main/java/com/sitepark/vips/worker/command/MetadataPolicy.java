package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import com.sitepark.vips.command.Metadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies the metadata policy to a derived image.
 *
 * <p>Three rules govern every command:
 *
 * <ul>
 *   <li>The IPTC block is written solely from the caller-supplied {@link Metadata}. Nothing is
 *       carried over from the source, so an image processed without a {@code Metadata} carries no
 *       IPTC at all.
 *   <li>Exactly one field is carried over from the source: the {@link XmpTag} whitelist, which holds
 *       {@code Iptc4xmpExt:DigitalSourceType}. It is re-emitted in a freshly built XMP packet.
 *   <li>Everything else is dropped — EXIF, the source XMP packet, Photoshop resource blocks.
 * </ul>
 *
 * <p>The ICC profile is the exception: it is kept, because dropping it would shift the colours of a
 * wide-gamut source.
 *
 * <p>The EXIF orientation is not carried over either, but not because it could not be: libvips
 * writes a synthesised baseline EXIF on save even when {@code exif-data} was dropped, and that block
 * has an Orientation slot. The handlers bake the rotation into the pixels with {@code autorot()} at
 * load instead, because every geometry parameter of a command — the resize width and height, the
 * border insets, the crop offsets — is expressed in display coordinates, while the stored pixels of
 * a rotated source are not. {@code ScaleTransformSupport} scales each axis to an exact requested
 * size, so applying those numbers to unrotated pixels squashes the image. Rotating first is what
 * makes the two agree, and it also matches {@code vips_thumbnail}, which the batch path uses and
 * which rotates upright by default.
 */
final class MetadataPolicy {

  private MetadataPolicy() {}

  /**
   * Replaces all metadata on {@code image} as described above. Mutates and returns {@code image} for
   * fluent chaining at the call site.
   *
   * <p>Must be given a per-output image — never an image shared between outputs.
   */
  static VImage apply(VImage image, MetadataContext context) {
    Map<XmpTag, List<String>> xmp = carriedXmp(context);

    dropNonWhitelisted(image);

    ImageMetadata.setBlob(image, ImageMetadata.IPTC, IptcBuilder.buildBlob(context.explicit()));
    ImageMetadata.setBlob(image, ImageMetadata.XMP, XmpBuilder.buildPacket(xmp));
    ImageMetadata.setBlob(image, ImageMetadata.ICC_PROFILE, context.source().iccProfile());
    return image;
  }

  /**
   * Removes every metadata block the policy does not cover.
   *
   * <p>Dropping {@code exif-data} alone is not enough. libvips also exposes the individual {@code
   * exif-ifd*} tags, the embedded EXIF thumbnail and the orientation as separate fields, and
   * rebuilds the EXIF block from those on save — so each has to go as well. What libvips then still
   * writes is a synthesised baseline EXIF (version, resolution, dimensions) that carries nothing
   * from the source.
   */
  private static void dropNonWhitelisted(VImage image) {
    for (String field : new ArrayList<>(image.getFields())) {
      if (isDropped(field)) {
        ImageMetadata.remove(image, field);
      }
    }
  }

  private static boolean isDropped(String field) {
    return ImageMetadata.EXIF.equals(field)
        || ImageMetadata.IPTC.equals(field)
        || ImageMetadata.XMP.equals(field)
        || ImageMetadata.PHOTOSHOP.equals(field)
        || ImageMetadata.JPEG_THUMBNAIL.equals(field)
        || ImageMetadata.ORIENTATION.equals(field)
        || field.startsWith(ImageMetadata.EXIF_FIELD_PREFIX);
  }

  /**
   * The whitelisted XMP properties to re-emit. Package-private for testing: this is pure and needs
   * no libvips.
   */
  static Map<XmpTag, List<String>> carriedXmp(MetadataContext context) {
    return context.source().xmp();
  }
}
