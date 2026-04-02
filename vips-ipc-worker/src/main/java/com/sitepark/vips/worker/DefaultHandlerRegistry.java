package com.sitepark.vips.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Config;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.Shutdown;
import com.sitepark.vips.command.Thumbnail;
import com.sitepark.vips.response.DebugInfo;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.Response;
import com.sitepark.vips.worker.command.CommandHandler;
import com.sitepark.vips.worker.command.ConfigHandler;
import com.sitepark.vips.worker.command.GetEnvironmentHandler;
import com.sitepark.vips.worker.command.ResizeHandler;
import com.sitepark.vips.worker.command.ScaleTransformBatchHandler;
import com.sitepark.vips.worker.command.ScaleTransformHandler;
import com.sitepark.vips.worker.command.ThumbnailHandler;
import java.io.PrintWriter;
import java.io.StringWriter;

@SuppressWarnings("PMD.AvoidCatchingThrowable")
class DefaultHandlerRegistry implements HandlerRegistry {

  private static final ObjectMapper CLI_MAPPER = new ObjectMapper();

  private final ConfigHandler configHandler;
  private final GetEnvironmentHandler getEnvironmentHandler;
  private final ResizeHandler resizeHandler;
  private final ThumbnailHandler thumbnailHandler;
  private final ScaleTransformHandler scaleTransformHandler;
  private final ScaleTransformBatchHandler scaleTransformBatchHandler;
  private final String workerJarCommand;

  DefaultHandlerRegistry(
      ConfigHandler configHandler,
      GetEnvironmentHandler getEnvironmentHandler,
      ResizeHandler resizeHandler,
      ThumbnailHandler thumbnailHandler,
      ScaleTransformHandler scaleTransformHandler,
      ScaleTransformBatchHandler scaleTransformBatchHandler,
      String workerJarCommand) {
    this.configHandler = configHandler;
    this.getEnvironmentHandler = getEnvironmentHandler;
    this.resizeHandler = resizeHandler;
    this.thumbnailHandler = thumbnailHandler;
    this.scaleTransformHandler = scaleTransformHandler;
    this.scaleTransformBatchHandler = scaleTransformBatchHandler;
    this.workerJarCommand = workerJarCommand;
  }

  @Override
  public Response dispatch(Command command) {
    try {
      return switch (command) {
        case Config c -> executeAndWrap(c, configHandler);
        case GetEnvironment g -> getEnvironmentHandler.handle(g);
        case Resize r -> executeAndWrap(r, resizeHandler);
        case Thumbnail t -> executeAndWrap(t, thumbnailHandler);
        case ScaleTransform s -> executeAndWrap(s, scaleTransformHandler);
        case ScaleTransformBatch b -> executeAndWrap(b, scaleTransformBatchHandler);
        case Shutdown ignored -> new OkResponse(null);
      };
    } catch (Throwable t) {
      StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));
      return new ErrorResponse(t.getClass().getSimpleName() + ": " + t.getMessage(), sw.toString());
    }
  }

  private <T extends Command> OkResponse executeAndWrap(T cmd, CommandHandler<T> handler) {
    handler.handle(cmd);
    if (!cmd.debug()) {
      return new OkResponse(null);
    }
    return new OkResponse(new DebugInfo(buildEchoCommand(cmd)));
  }

  private String buildEchoCommand(Command cmd) {
    try {
      String json = CLI_MAPPER.writerFor(Command.class).writeValueAsString(cmd);
      ObjectNode node = (ObjectNode) CLI_MAPPER.readTree(json);
      node.remove("debug");
      return "echo '" + CLI_MAPPER.writeValueAsString(node) + "' | " + workerJarCommand;
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
