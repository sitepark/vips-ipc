package com.sitepark.vips.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Shutdown;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Runs the JSON command loop: reads commands from {@code in}, writes responses to {@code out}. */
@SuppressWarnings("PMD.AssignmentInOperand")
public class Worker {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HandlerRegistry registry;

  public Worker(HandlerRegistry registry) {
    this.registry = registry;
  }

  /**
   * Reads newline-delimited JSON commands from {@code in} and writes JSON responses to {@code out}
   * until a {@link Shutdown} command is received or the stream ends.
   *
   * @throws java.io.IOException if JSON parsing or serialization fails
   */
  public void run(InputStream in, PrintStream out) throws IOException {
    try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Command cmd = MAPPER.readValue(line, Command.class);
        out.println(MAPPER.writeValueAsString(registry.dispatch(cmd)));
        out.flush();
        if (cmd instanceof Shutdown) {
          break;
        }
      }
    }
  }
}
