package com.sitepark.vips.worker;

import app.photofox.vipsffm.Vips;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SuppressWarnings({
  "PMD.UseUtilityClass",
  "PMD.SystemPrintln",
  "PMD.AvoidCatchingGenericException",
  "PMD.DoNotTerminateVM"
})
public class Main {

  static void main(String... args) {
    try {
      new Worker(new HandlerRegistryDefaultFactory().create())
          .run(System.in, new PrintStream(System.out, true, StandardCharsets.UTF_8));
    } catch (Exception e) {
      System.err.println("Fatal error in worker: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    } finally {
      Vips.shutdown();
    }
  }
}
