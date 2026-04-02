package com.sitepark.vips.worker;

import app.photofox.vipsffm.Vips;
import java.io.PrintStream;
import java.net.URISyntaxException;
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
      new Worker(new HandlerRegistryDefaultFactory(resolveWorkerJarCommand()).create())
          .run(System.in, new PrintStream(System.out, true, StandardCharsets.UTF_8));
    } catch (Exception e) {
      System.err.println("Fatal error in worker: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    } finally {
      Vips.shutdown();
    }
  }

  @SuppressWarnings("PMD.LawOfDemeter")
  private static String resolveWorkerJarCommand() {
    String java = ProcessHandle.current().info().command().orElse("java");
    try {
      java += " --enable-native-access=ALL-UNNAMED";
      var source = Main.class.getProtectionDomain().getCodeSource();
      if (source == null) {
        return java + " -jar worker.jar";
      }
      var location = source.getLocation();
      if (location == null) {
        return java + " -jar worker.jar";
      }
      return java + " -jar " + location.toURI().getPath();
    } catch (URISyntaxException e) {
      return java + " -jar worker.jar";
    }
  }
}
