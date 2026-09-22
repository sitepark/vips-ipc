package com.sitepark.vips.worker.command;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VipsError;
import app.photofox.vipsffm.VipsHelper;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** Reads and writes raw libvips metadata fields on a {@link VImage}. */
final class ImageMetadata {

  /** The Photoshop IRB holding IPTC IIM data; libvips writes it to JPEG {@code APP13}. */
  static final String IPTC = "iptc-data";

  /** The XMP packet. */
  static final String XMP = "xmp-data";

  /** The raw EXIF block. */
  static final String EXIF = "exif-data";

  /** The embedded ICC colour profile. */
  static final String ICC_PROFILE = "icc-profile-data";

  /** Photoshop resource data some loaders expose separately from {@link #IPTC}. */
  static final String PHOTOSHOP = "photoshop-data";

  /** Prefix of the individual EXIF tag fields libvips derives from {@link #EXIF} on load. */
  static final String EXIF_FIELD_PREFIX = "exif-ifd";

  /**
   * The thumbnail embedded in the source EXIF. libvips exposes it as its own field and writes it
   * back as IFD1, so it survives removal of {@link #EXIF} and would otherwise ship a small copy of
   * the original image inside every derivative.
   */
  static final String JPEG_THUMBNAIL = "jpeg-thumbnail-data";

  /**
   * The EXIF orientation. The handlers bake the rotation into the pixels with {@code autorot()}, so
   * a leftover tag would rotate the output a second time.
   */
  static final String ORIENTATION = "orientation";

  /** Sanity bound on a metadata block, guarding against a bogus length from libvips. */
  private static final long MAX_BLOB_BYTES = 64L * 1024 * 1024;

  private ImageMetadata() {}

  /** Returns the blob stored under {@code name}, or an empty array when the field is absent. */
  static byte[] getBlob(VImage image, String name) {
    // Read through vips_image_get_blob rather than VImage.getBlob: the latter hands back a VBlob
    // whose reported byteSize is garbage for the fields libvips sets internally, which then fails
    // as "Segment is too large to wrap as ByteBuffer".
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment address = image.getUnsafeStructAddress();
      if (VipsHelper.image_get_typeof(arena, address, name) == 0) {
        return new byte[0];
      }
      MemorySegment dataOut = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment lengthOut = arena.allocate(ValueLayout.JAVA_LONG);
      if (VipsHelper.image_get_blob(arena, address, name, dataOut, lengthOut) != 0) {
        return new byte[0];
      }
      long length = lengthOut.get(ValueLayout.JAVA_LONG, 0);
      MemorySegment data = dataOut.get(ValueLayout.ADDRESS, 0);
      if (length <= 0 || length > MAX_BLOB_BYTES || MemorySegment.NULL.equals(data)) {
        return new byte[0];
      }
      return data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
    } catch (VipsError e) {
      // A field libvips reports as present but cannot hand over is treated as absent;
      // metadata must never fail an image operation.
      return new byte[0];
    }
  }

  /**
   * Stores {@code data} under {@code name}, or removes the field when {@code data} is {@code null}
   * or empty.
   *
   * <p>Uses {@code vips_image_set_blob_copy}, so libvips owns a copy immediately and the temporary
   * arena can close right after the call.
   */
  static void setBlob(VImage image, String name, byte[] data) {
    if (data == null || data.length == 0) {
      remove(image, name);
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      var segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      VipsHelper.image_set_blob_copy(
          arena, image.getUnsafeStructAddress(), name, segment, data.length);
    }
  }

  /** Removes {@code name}. Returns whether the field was present. */
  static boolean remove(VImage image, String name) {
    // vips_image_remove reports a missing field by returning false rather than failing.
    return image.remove(name);
  }
}
