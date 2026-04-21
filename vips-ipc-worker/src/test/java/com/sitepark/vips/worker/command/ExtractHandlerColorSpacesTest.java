package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sitepark.vips.command.Extract;
import com.sitepark.vips.command.ExtractResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExtractHandlerColorSpacesTest {

  private static Map<String, ExtractResult> results;

  @BeforeAll
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  static void runHandler() throws URISyntaxException, IOException {
    ExtractHandler handler = new ExtractHandler();
    results = new LinkedHashMap<>();
    for (String name : listImageNames()) {
      var url = ExtractHandlerColorSpacesTest.class.getResource("/color-spaces-tests/" + name);
      Path path = Path.of(url.toURI());
      results.put(name, handler.handle(new Extract(path.toString(), 5, false)));
    }
  }

  static Stream<String> imageNames() throws URISyntaxException, IOException {
    return listImageNames().stream();
  }

  private static List<String> listImageNames() throws URISyntaxException, IOException {
    var url = ExtractHandlerColorSpacesTest.class.getResource("/color-spaces-tests/");
    Path dir = Path.of(url.toURI());
    List<String> names;
    try (Stream<Path> stream = Files.list(dir)) {
      names = stream.sorted().map(p -> p.getFileName().toString()).toList();
    }
    return names;
  }

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

  @ParameterizedTest
  @MethodSource("imageNames")
  void testColorPaletteIsNotEmpty(String image) {
    assertFalse(
        results.get(image).colorPalette().colors().isEmpty(),
        "Color palette for " + image + " should not be empty after processing");
  }
}
