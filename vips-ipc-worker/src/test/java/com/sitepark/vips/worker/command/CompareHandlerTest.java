package com.sitepark.vips.worker.command;

import static org.junit.jupiter.api.Assertions.*;

import com.sitepark.vips.command.Compare;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CompareHandlerTest {
  @Test
  void test() {
    Path a = Path.of("src/test/resources/compare/a.png");
    Path b = Path.of("src/test/resources/compare/b.png");
    Path result = Path.of("target/test-output/diff.png");
    CompareHandler handler = new CompareHandler();
    var compareResult = handler.handle(Compare.of(a, b, result, new Compare.Color(23, 77, 123)));
    assertNotNull(compareResult, "CompareHandler should return a non-null result");
  }
}
