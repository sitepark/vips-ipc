package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.file.Path;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record Compare(
    String imageA, String imageB, String result, Color highlightColor, boolean debug)
    implements Command<CompareResult> {

  public static final Color DEFAULT_HIGHLIGHT_COLOR = new Color(255, 20, 147);

  public record Color(int r, int g, int b) {}

  public static Compare of(Path imageA, Path imageB) {
    return new Compare(
        imageA.toAbsolutePath().toString(), imageB.toAbsolutePath().toString(), null, null, false);
  }

  public static Compare of(Path imageA, Path imageB, Path result) {
    return new Compare(
        imageA.toAbsolutePath().toString(),
        imageB.toAbsolutePath().toString(),
        result == null ? null : result.toAbsolutePath().toString(),
        null,
        false);
  }

  public static Compare of(Path imageA, Path imageB, Path result, Color highlightColor) {
    return new Compare(
        imageA.toAbsolutePath().toString(),
        imageB.toAbsolutePath().toString(),
        result == null ? null : result.toAbsolutePath().toString(),
        highlightColor,
        false);
  }
}
