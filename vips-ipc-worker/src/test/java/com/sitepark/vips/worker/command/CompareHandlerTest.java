package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.*;

import com.sitepark.vips.command.Compare;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CompareHandlerTest {
  @Test
  void test() {
    /*
    Path a = Path.of("src/test/resources/compare/a.png");
    Path b = Path.of("src/test/resources/compare/b.png");
    */

    Path a =
        Path.of(
            "/home/veltrup/git/imgfile/src/test/resources/references/ScaleWithBackgroundTest/scale-with-transparency-256x128-padding-fit/homer.png/256x128-padding-fit.png");
    Path b =
        Path.of(
            "/home/veltrup/git/imgfile/target/test/images/ScaleWithBackgroundTest/scale-with-transparency-256x128-padding-fit/homer.png/256x128-padding-fit.png");

    Path result = Path.of("target/test-output/diff.png");
    CompareHandler handler = new CompareHandler();
    var compareResult = handler.handle(Compare.of(a, b, result, new Compare.Color(23, 77, 123)));
    assertNotNull(compareResult, "CompareHandler should return a non-null result");
  }
}
