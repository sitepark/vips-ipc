package com.sitepark.vips.worker.command;

import java.io.File;
import java.net.URL;

/** Resolves a fixture on the classpath to an absolute filesystem path. */
final class Fixtures {

  private Fixtures() {}

  @SuppressWarnings("PMD.LawOfDemeter")
  static String path(String name) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL url = cl.getResource(name);
    if (url == null) {
      throw new IllegalStateException("Test resource not found: " + name);
    }
    return new File(url.getFile()).getAbsolutePath();
  }
}
