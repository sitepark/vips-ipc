package com.sitepark.vips.fakeworker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Shutdown;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Fake worker for testing. Behaviour is selected via the first command-line argument (mode):
 *
 * <ul>
 *   <li>{@code error} (default) – returns an ErrorResponse for every non-Shutdown command
 *   <li>{@code ok} – returns OkResponse for every command
 *   <li>{@code crash} – exits immediately with code 1, without reading stdin
 *   <li>{@code crash-after-first} – returns OkResponse for the first non-Shutdown command, then
 *       exits without responding (triggers the manager's retry-with-new-process path)
 *   <li>{@code unexpected} – returns a VipsEnvironmentResponse for every non-Shutdown command
 *   <li>{@code timeout} – reads one line from stdin but never writes, blocking indefinitely
 * </ul>
 */
@SuppressWarnings({
  "PMD.UseUtilityClass",
  "PMD.DoNotTerminateVM",
  "PMD.AssignmentInOperand",
  "PMD.CloseResource"
})
public class Main {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String MODE_CRASH = "crash";
  private static final String MODE_TIMEOUT = "timeout";

  private static final String OK_RESPONSE = "{\"status\":\"ok\"}";
  private static final String ERROR_RESPONSE =
      "{\"status\":\"error\","
          + "\"message\":\"UnsatisfiedLinkError: no vips in java.library.path\","
          + "\"stackTrace\":\"\"}";
  private static final String ENVIRONMENT_RESPONSE =
      "{\"status\":\"environment\","
          + "\"vipsVersion\":\"fake-8.15.1\","
          + "\"supportedFormats\":[\"jpg\",\"png\"]}";

  static void main(String... args) throws IOException {
    String mode = args.length > 0 ? args[0] : "error";

    if (MODE_CRASH.equals(mode)) {
      System.exit(1);
    }

    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

      if (MODE_TIMEOUT.equals(mode)) {
        reader.readLine(); // block without ever responding
        return;
      }

      boolean firstCommandHandled = false;
      String line;
      while ((line = reader.readLine()) != null) {
        Command cmd = MAPPER.readValue(line, Command.class);
        if (cmd instanceof Shutdown) {
          out.println(OK_RESPONSE);
          break;
        }
        if ("crash-after-first".equals(mode) && firstCommandHandled) {
          System.exit(1); // exit without writing – triggers null-response retry in manager
        }
        out.println(responseFor(mode));
        firstCommandHandled = true;
      }
    }
  }

  private static String responseFor(String mode) {
    return switch (mode) {
      case "ok", "crash-after-first" -> OK_RESPONSE;
      case "unexpected" -> ENVIRONMENT_RESPONSE;
      default -> ERROR_RESPONSE;
    };
  }
}
