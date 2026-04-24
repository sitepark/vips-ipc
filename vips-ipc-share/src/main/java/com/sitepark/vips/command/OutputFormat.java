package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Format-specific settings for a scale-transform output. Each subtype carries only the options
 * relevant to that format.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OutputFormat.JpegFormat.class, name = "jpg"),
  @JsonSubTypes.Type(value = OutputFormat.WebpFormat.class, name = "webp"),
  @JsonSubTypes.Type(value = OutputFormat.PngFormat.class, name = "png"),
  @JsonSubTypes.Type(value = OutputFormat.GifFormat.class, name = "gif"),
  @JsonSubTypes.Type(value = OutputFormat.AvifFormat.class, name = "avif"),
})
@SuppressWarnings("PMD.TooManyMethods")
public sealed interface OutputFormat
    permits OutputFormat.JpegFormat,
        OutputFormat.WebpFormat,
        OutputFormat.PngFormat,
        OutputFormat.GifFormat,
        OutputFormat.AvifFormat {

  int DEFAULT_QUALITY = 82;

  String PROP_QUALITY = "quality";
  String PROP_INTERLACE = "interlace";
  String PROP_LOSSLESS = "lossless";
  String PROP_STRIP = "strip";
  String PROP_APPEND_EXTENSION = "appendExtension";

  /** Returns the file extension for this format (e.g. {@code "jpg"}). */
  String extension();

  /** Whether to append the format's file extension to the target path. Defaults to {@code true}. */
  boolean appendExtension();

  /** Whether to strip all metadata (EXIF, IPTC, XMP) from the output. Defaults to {@code false}. */
  boolean strip();

  // ── Factory helpers ─────────────────────────────────────────────

  static JpegFormat jpeg() {
    return new JpegFormat(DEFAULT_QUALITY, true, false, true);
  }

  static JpegFormat jpeg(int quality) {
    return new JpegFormat(quality, true, false, true);
  }

  static WebpFormat webp() {
    return new WebpFormat(DEFAULT_QUALITY, false, false, true);
  }

  static WebpFormat webp(int quality) {
    return new WebpFormat(quality, false, false, true);
  }

  static WebpFormat webpLossless() {
    return new WebpFormat(DEFAULT_QUALITY, true, false, true);
  }

  static PngFormat png() {
    return new PngFormat(false, true);
  }

  static GifFormat gif() {
    return new GifFormat(false, true);
  }

  static AvifFormat avif() {
    return new AvifFormat(DEFAULT_QUALITY, false, false, true);
  }

  static AvifFormat avif(int quality) {
    return new AvifFormat(quality, false, false, true);
  }

  static AvifFormat avifLossless() {
    return new AvifFormat(DEFAULT_QUALITY, true, false, true);
  }

  // ── Concrete record types ────────────────────────────────────────

  record JpegFormat(
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = DefaultQualityFilter.class)
          int quality,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
          boolean interlace,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean strip,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
          boolean appendExtension)
      implements OutputFormat {

    @JsonCreator
    static JpegFormat jsonCreate(
        @JsonProperty(PROP_QUALITY) Integer quality,
        @JsonProperty(PROP_INTERLACE) Boolean interlace,
        @JsonProperty(PROP_STRIP) Boolean strip,
        @JsonProperty(PROP_APPEND_EXTENSION) Boolean appendExtension) {
      return new JpegFormat(
          quality != null ? quality : DEFAULT_QUALITY,
          interlace == null || interlace,
          strip != null && strip,
          appendExtension == null || appendExtension);
    }

    public JpegFormat withQuality(int quality) {
      return new JpegFormat(quality, this.interlace, this.strip, this.appendExtension);
    }

    public JpegFormat withInterlace(boolean interlace) {
      return new JpegFormat(this.quality, interlace, this.strip, this.appendExtension);
    }

    public JpegFormat withStrip(boolean strip) {
      return new JpegFormat(this.quality, this.interlace, strip, this.appendExtension);
    }

    public JpegFormat withAppendExtension(boolean appendExtension) {
      return new JpegFormat(this.quality, this.interlace, this.strip, appendExtension);
    }

    @Override
    public String extension() {
      return "jpg";
    }
  }

  record WebpFormat(
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = DefaultQualityFilter.class)
          int quality,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean lossless,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean strip,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
          boolean appendExtension)
      implements OutputFormat {

    @JsonCreator
    static WebpFormat jsonCreate(
        @JsonProperty(PROP_QUALITY) Integer quality,
        @JsonProperty(PROP_LOSSLESS) Boolean lossless,
        @JsonProperty(PROP_STRIP) Boolean strip,
        @JsonProperty(PROP_APPEND_EXTENSION) Boolean appendExtension) {
      return new WebpFormat(
          quality != null ? quality : DEFAULT_QUALITY,
          lossless != null && lossless,
          strip != null && strip,
          appendExtension == null || appendExtension);
    }

    public WebpFormat withQuality(int quality) {
      return new WebpFormat(quality, this.lossless, this.strip, this.appendExtension);
    }

    public WebpFormat withLossless(boolean lossless) {
      return new WebpFormat(this.quality, lossless, this.strip, this.appendExtension);
    }

    public WebpFormat withStrip(boolean strip) {
      return new WebpFormat(this.quality, this.lossless, strip, this.appendExtension);
    }

    public WebpFormat withAppendExtension(boolean appendExtension) {
      return new WebpFormat(this.quality, this.lossless, this.strip, appendExtension);
    }

    @Override
    public String extension() {
      return "webp";
    }
  }

  record PngFormat(
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean strip,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
          boolean appendExtension)
      implements OutputFormat {

    @JsonCreator
    static PngFormat jsonCreate(
        @JsonProperty(PROP_STRIP) Boolean strip,
        @JsonProperty(PROP_APPEND_EXTENSION) Boolean appendExtension) {
      return new PngFormat(strip != null && strip, appendExtension == null || appendExtension);
    }

    public PngFormat withStrip(boolean strip) {
      return new PngFormat(strip, this.appendExtension);
    }

    public PngFormat withAppendExtension(boolean appendExtension) {
      return new PngFormat(this.strip, appendExtension);
    }

    @Override
    public String extension() {
      return "png";
    }
  }

  record GifFormat(
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean strip,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
          boolean appendExtension)
      implements OutputFormat {

    @JsonCreator
    static GifFormat jsonCreate(
        @JsonProperty(PROP_STRIP) Boolean strip,
        @JsonProperty(PROP_APPEND_EXTENSION) Boolean appendExtension) {
      return new GifFormat(strip != null && strip, appendExtension == null || appendExtension);
    }

    public GifFormat withStrip(boolean strip) {
      return new GifFormat(strip, this.appendExtension);
    }

    public GifFormat withAppendExtension(boolean appendExtension) {
      return new GifFormat(this.strip, appendExtension);
    }

    @Override
    public String extension() {
      return "gif";
    }
  }

  record AvifFormat(
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = DefaultQualityFilter.class)
          int quality,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean lossless,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = FalseFilter.class)
          boolean strip,
      @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = TrueFilter.class)
          boolean appendExtension)
      implements OutputFormat {

    @JsonCreator
    static AvifFormat jsonCreate(
        @JsonProperty(PROP_QUALITY) Integer quality,
        @JsonProperty(PROP_LOSSLESS) Boolean lossless,
        @JsonProperty(PROP_STRIP) Boolean strip,
        @JsonProperty(PROP_APPEND_EXTENSION) Boolean appendExtension) {
      return new AvifFormat(
          quality != null ? quality : DEFAULT_QUALITY,
          lossless != null && lossless,
          strip != null && strip,
          appendExtension == null || appendExtension);
    }

    public AvifFormat withQuality(int quality) {
      return new AvifFormat(quality, this.lossless, this.strip, this.appendExtension);
    }

    public AvifFormat withLossless(boolean lossless) {
      return new AvifFormat(this.quality, lossless, this.strip, this.appendExtension);
    }

    public AvifFormat withStrip(boolean strip) {
      return new AvifFormat(this.quality, this.lossless, strip, this.appendExtension);
    }

    public AvifFormat withAppendExtension(boolean appendExtension) {
      return new AvifFormat(this.quality, this.lossless, this.strip, appendExtension);
    }

    @Override
    public String extension() {
      return "avif";
    }
  }

  // ── Jackson filter helpers ───────────────────────────────────────

  /** Excludes {@code quality} from JSON when it equals the default value of 82. */
  class DefaultQualityFilter {
    @Override
    public boolean equals(Object obj) {
      return obj instanceof Integer i && i == DEFAULT_QUALITY;
    }

    @Override
    public int hashCode() {
      return DEFAULT_QUALITY;
    }
  }

  /** Excludes a {@code boolean} field from JSON when it is {@code false}. */
  class FalseFilter {
    @Override
    public boolean equals(Object obj) {
      return Boolean.FALSE.equals(obj);
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }

  /** Excludes a {@code boolean} field from JSON when it is {@code true}. */
  class TrueFilter {
    @Override
    public boolean equals(Object obj) {
      return Boolean.TRUE.equals(obj);
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }
}
