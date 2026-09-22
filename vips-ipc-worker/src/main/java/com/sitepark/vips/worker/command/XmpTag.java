package com.sitepark.vips.worker.command;

/**
 * The XMP properties carried over from a source image into every derived image.
 *
 * <p>This is the hardcoded whitelist. It holds one entry: {@code Iptc4xmpExt:DigitalSourceType}, the
 * provenance marker for AI-generated media. It is whitelisted precisely because it has no IPTC IIM
 * equivalent — an XMP packet is the only way it can travel, and the source packet itself is dropped.
 *
 * <p>Title, copyright and description are not listed here: they are written from the caller-supplied
 * {@link com.sitepark.vips.command.Metadata} rather than copied from the source.
 */
enum XmpTag {

  /** {@code Iptc4xmpExt:DigitalSourceType}. */
  DIGITAL_SOURCE_TYPE(Namespace.IPTC_EXT, "DigitalSourceType");

  /** XMP namespace URIs and their conventional prefixes. */
  enum Namespace {
    IPTC_EXT("http://iptc.org/std/Iptc4xmpExt/2008-02-29/", "Iptc4xmpExt");

    private final String namespaceUri;
    private final String shortPrefix;

    Namespace(String namespaceUri, String shortPrefix) {
      this.namespaceUri = namespaceUri;
      this.shortPrefix = shortPrefix;
    }

    String uri() {
      return this.namespaceUri;
    }

    String prefix() {
      return this.shortPrefix;
    }
  }

  private final Namespace tagNamespace;
  private final String tagLocalName;

  XmpTag(Namespace tagNamespace, String tagLocalName) {
    this.tagNamespace = tagNamespace;
    this.tagLocalName = tagLocalName;
  }

  Namespace namespace() {
    return this.tagNamespace;
  }

  String localName() {
    return this.tagLocalName;
  }

  /** Returns the whitelisted tag for a namespace URI and local name, or {@code null}. */
  static XmpTag of(String namespaceUri, String localName) {
    for (XmpTag tag : values()) {
      if (tag.tagNamespace.uri().equals(namespaceUri) && tag.tagLocalName.equals(localName)) {
        return tag;
      }
    }
    return null;
  }
}
