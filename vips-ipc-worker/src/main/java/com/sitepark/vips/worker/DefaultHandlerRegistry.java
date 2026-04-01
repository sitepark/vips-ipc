package com.sitepark.vips.worker;

import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Config;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.ScaleTransform;
import com.sitepark.vips.command.ScaleTransformBatch;
import com.sitepark.vips.command.Shutdown;
import com.sitepark.vips.command.Thumbnail;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.Response;
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

  private final ConfigHandler configHandler;
  private final GetEnvironmentHandler getEnvironmentHandler;
  private final ResizeHandler resizeHandler;
  private final ThumbnailHandler thumbnailHandler;
  private final ScaleTransformHandler scaleTransformHandler;
  private final ScaleTransformBatchHandler scaleTransformBatchHandler;

  DefaultHandlerRegistry(
      ConfigHandler configHandler,
      GetEnvironmentHandler getEnvironmentHandler,
      ResizeHandler resizeHandler,
      ThumbnailHandler thumbnailHandler,
      ScaleTransformHandler scaleTransformHandler,
      ScaleTransformBatchHandler scaleTransformBatchHandler) {
    this.configHandler = configHandler;
    this.getEnvironmentHandler = getEnvironmentHandler;
    this.resizeHandler = resizeHandler;
    this.thumbnailHandler = thumbnailHandler;
    this.scaleTransformHandler = scaleTransformHandler;
    this.scaleTransformBatchHandler = scaleTransformBatchHandler;
  }

  @Override
  public Response dispatch(Command command) {
    try {
      return switch (command) {
        case Config c -> {
          configHandler.handle(c);
          yield new OkResponse();
        }
        case GetEnvironment g -> getEnvironmentHandler.handle(g);
        case Resize r -> {
          resizeHandler.handle(r);
          yield new OkResponse();
        }
        case Thumbnail t -> {
          thumbnailHandler.handle(t);
          yield new OkResponse();
        }
        case ScaleTransform s -> {
          scaleTransformHandler.handle(s);
          yield new OkResponse();
        }
        case ScaleTransformBatch b -> {
          scaleTransformBatchHandler.handle(b);
          yield new OkResponse();
        }
        case Shutdown ignored -> new OkResponse();
      };
    } catch (Throwable t) {
      StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));
      return new ErrorResponse(t.getClass().getSimpleName() + ": " + t.getMessage(), sw.toString());
    }
  }
}
