package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sitepark.vips.command.ColorPaletteEntry;
import com.sitepark.vips.command.Extract;
import com.sitepark.vips.command.ExtractResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExtractHandlerTest {

  private static Map<String, ExtractResult> results;
  private static Map<String, Path> imagePaths;

  @BeforeAll
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  static void runHandler() throws URISyntaxException, IOException {
    ExtractHandler handler = new ExtractHandler();
    results = new LinkedHashMap<>();
    imagePaths = new LinkedHashMap<>();
    for (String name : listImageNames()) {
      var url = ExtractHandlerTest.class.getResource("/color-palette-tests/" + name);
      Path path = Path.of(url.toURI());
      imagePaths.put(name, path);
      results.put(name, handler.handle(new Extract(path.toString(), 5, false)));
    }
  }

  static Stream<String> imageNames() throws URISyntaxException, IOException {
    return listImageNames().stream();
  }

  private static List<String> listImageNames() throws URISyntaxException, IOException {
    var url = ExtractHandlerTest.class.getResource("/color-palette-tests/");
    Path dir = Path.of(url.toURI());
    List<String> names;
    try (Stream<Path> stream = Files.list(dir)) {
      names = stream.sorted().map(p -> p.getFileName().toString()).toList();
    }
    return names;
  }

  // ── color palette entries ─────────────────────────────────────

  @ParameterizedTest
  @MethodSource("imageNames")
  void testColorPaletteIsNotEmpty(String image) {
    assertFalse(
        results.get(image).colorPalette().colors().isEmpty(),
        "Color palette for " + image + " should contain at least one entry");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testColorPaletteHasAtMost32Colors(String image) {
    assertTrue(
        results.get(image).colorPalette().colors().size() <= 32,
        "Color palette for "
            + image
            + " should have at most 32 colors (GIF bitdepth=5 → 2^5 palette slots)");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testAllPixelCountsArePositive(String image) {
    assertTrue(
        results.get(image).colorPalette().colors().stream().allMatch(e -> e.pixelCount() > 0),
        "Every color palette entry for " + image + " should have a positive pixel count");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testColorsAreSortedByPixelCountDescending(String image) {
    List<ColorPaletteEntry> colors = results.get(image).colorPalette().colors();
    assertTrue(
        IntStream.range(0, colors.size() - 1)
            .allMatch(i -> colors.get(i).pixelCount() >= colors.get(i + 1).pixelCount()),
        "Color palette entries for " + image + " should be sorted by pixelCount descending");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testAllRgbValuesAreInValidRange(String image) {
    assertTrue(
        results.get(image).colorPalette().colors().stream()
            .allMatch(
                e ->
                    e.red() >= 0
                        && e.red() <= 255
                        && e.green() >= 0
                        && e.green() <= 255
                        && e.blue() >= 0
                        && e.blue() <= 255),
        "All RGB values for " + image + " should be in range [0, 255]");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testPercentagesSumToOneHundred(String image) {
    double sum =
        results.get(image).colorPalette().colors().stream()
            .mapToDouble(ColorPaletteEntry::percentage)
            .sum();
    assertEquals(100.0, sum, 0.1, "Percentages for " + image + " should sum to approximately 100%");
  }

  // ── image metadata ─────────────────────────────────────────────

  @ParameterizedTest
  @MethodSource("imageNames")
  void testWidthIsPositive(String image) {
    assertTrue(results.get(image).width() > 0, "Width of " + image + " should be positive");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testHeightIsPositive(String image) {
    assertTrue(results.get(image).height() > 0, "Height of " + image + " should be positive");
  }

  @ParameterizedTest
  @MethodSource("imageNames")
  void testChannelsIsPositive(String image) {
    assertTrue(results.get(image).channels() > 0, "Channels of " + image + " should be positive");
  }

  // ── HTML report ────────────────────────────────────────────────

  @AfterAll
  @SuppressWarnings({
    "PMD.ConsecutiveAppendsShouldReuse",
    "PMD.ConsecutiveLiteralAppends",
    "PMD.SystemPrintln"
  })
  static void writeHtmlReport() throws IOException {
    StringBuilder html = new StringBuilder(4096);
    html.append(
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <title>Color Palette Report</title>
          <style>
            *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              background: #111827; color: #e5e7eb; padding: 2rem;
            }
            h1 { font-size: 1.5rem; font-weight: 600; margin-bottom: 2rem; color: #f9fafb; }
            .grid {
              display: grid;
              grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
              gap: 1.5rem;
            }
            .card {
              background: #1f2937; border-radius: 12px; overflow: hidden;
              box-shadow: 0 4px 16px rgba(0,0,0,.4);
            }
            .card img {
              width: 100%; height: 200px; object-fit: cover; display: block;
            }
            .card-body { padding: 1rem; }
            .card-title {
              font-size: 0.75rem; color: #9ca3af; margin-bottom: 0.3rem;
              white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            }
            .card-meta { font-size: 0.72rem; color: #6b7280; margin-bottom: 1rem; }
            .color-bar {
              display: flex; height: 10px; border-radius: 5px; overflow: hidden;
              margin-bottom: 0.75rem;
            }
            .color-bar-seg { flex-shrink: 0; }
            .swatches { display: flex; flex-wrap: wrap; gap: 6px; }
            .swatch { display: flex; flex-direction: column; align-items: center; gap: 2px; }
            .swatch-block {
              width: 40px; height: 40px; border-radius: 6px;
              box-shadow: 0 1px 4px rgba(0,0,0,.5);
            }
            .swatch-hex { font-size: 0.55rem; color: #9ca3af; font-family: monospace; }
            .swatch-pct { font-size: 0.6rem; color: #d1d5db; font-weight: 600; }
          </style>
        </head>
        <body>
          <h1>Color Palette Report</h1>
          <div class="grid">
        """);

    for (Map.Entry<String, ExtractResult> entry : results.entrySet()) {
      String image = entry.getKey();
      ExtractResult r = entry.getValue();
      Path imgPath = imagePaths.get(image);
      List<ColorPaletteEntry> colors = r.colorPalette().colors();

      html.append("    <div class=\"card\">\n");
      html.append("      <img src=\"")
          .append(imgPath.toUri())
          .append("\" alt=\"")
          .append(image)
          .append("\">\n");
      html.append("      <div class=\"card-body\">\n");
      html.append("        <div class=\"card-title\">").append(image).append("</div>\n");
      html.append("        <div class=\"card-meta\">")
          .append(r.width())
          .append(" × ")
          .append(r.height())
          .append(" &nbsp;·&nbsp; ")
          .append(r.channels())
          .append(" ch")
          .append(r.hasAlpha() ? " &nbsp;·&nbsp; alpha" : "")
          .append(" &nbsp;·&nbsp; ")
          .append(colors.size())
          .append(" colors")
          .append("</div>\n");

      // stacked color bar
      html.append("        <div class=\"color-bar\">\n");
      for (ColorPaletteEntry e : colors) {
        html.append("          <div class=\"color-bar-seg\" style=\"width:")
            .append(String.format("%.2f", e.percentage()))
            .append("%;background-color:")
            .append(toHex(e))
            .append(";\"></div>\n");
      }
      html.append("        </div>\n");

      // color swatches
      html.append("        <div class=\"swatches\">\n");
      for (ColorPaletteEntry e : colors) {
        String hex = toHex(e);
        html.append("          <div class=\"swatch\">\n");
        html.append("            <div class=\"swatch-block\" style=\"background-color:")
            .append(hex)
            .append(";\"></div>\n");
        html.append("            <span class=\"swatch-hex\">").append(hex).append("</span>\n");
        html.append("            <span class=\"swatch-pct\">")
            .append(String.format("%.1f", e.percentage()))
            .append("%</span>\n");
        html.append("          </div>\n");
      }
      html.append("        </div>\n");
      html.append("      </div>\n");
      html.append("    </div>\n");
    }

    html.append(
        """
          </div>
        </body>
        </html>
        """);

    Path report = Path.of("target/color-palette-report.html");
    Files.createDirectories(report.getParent());
    Files.writeString(report, html.toString());
    System.out.println("Color palette report written to: file://" + report.toAbsolutePath());
  }

  private static String toHex(ColorPaletteEntry e) {
    return String.format("#%02X%02X%02X", e.red(), e.green(), e.blue());
  }
}
