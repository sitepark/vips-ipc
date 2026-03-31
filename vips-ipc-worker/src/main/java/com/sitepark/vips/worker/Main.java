package com.sitepark.vips.worker;

import app.photofox.vipsffm.Vips;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.Shutdown;
import com.sitepark.vips.command.Thumbnail;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.Response;
import com.sitepark.vips.worker.command.ResizeHandler;
import com.sitepark.vips.worker.command.ThumbnailHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

@SuppressWarnings({
  "PMD.UseUtilityClass",
  "PMD.AssignmentInOperand",
  "PMD.SystemPrintln",
  "PMD.AvoidCatchingGenericException",
  "PMD.DoNotTerminateVM"
})
public class Main {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static void main(String... args) {
    try (var reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

      String line;
      while ((line = reader.readLine()) != null) {
        Command cmd = MAPPER.readValue(line, Command.class);
        System.out.println(MAPPER.writeValueAsString(processCommand(cmd)));
        System.out.flush(); // required: response must not remain buffered
        if (cmd instanceof Shutdown) {
          break;
        }
      }

    } catch (Exception e) {
      System.err.println("Fatal error in worker: " + e.getMessage());
      e.printStackTrace(System.err);
      Vips.shutdown();
      System.exit(1);
    }

    Vips.shutdown();
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  private static Response processCommand(Command cmd) {
    try {
      switch (cmd) {
        case Resize r -> new ResizeHandler().handle(r);
        case Thumbnail t -> new ThumbnailHandler().handle(t);
        case Shutdown ignored -> {} // graceful shutdown: respond ok, loop exits after this
      }

      return new OkResponse();

    } catch (Throwable t) {
      StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));
      return new ErrorResponse(t.getClass().getSimpleName() + ": " + t.getMessage(), sw.toString());
    }
  }
}
