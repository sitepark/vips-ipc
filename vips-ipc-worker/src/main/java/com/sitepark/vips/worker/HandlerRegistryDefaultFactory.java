package com.sitepark.vips.worker;

import com.sitepark.vips.handler.HandlerRegistry;
import com.sitepark.vips.handler.HandlerRegistryFactory;
import com.sitepark.vips.worker.command.CompareHandler;
import com.sitepark.vips.worker.command.ExtractHandler;
import com.sitepark.vips.worker.command.GetEnvironmentHandler;
import com.sitepark.vips.worker.command.ResizeHandler;
import com.sitepark.vips.worker.command.ScaleTransformBatchHandler;
import com.sitepark.vips.worker.command.ScaleTransformHandler;
import com.sitepark.vips.worker.command.ThumbnailHandler;

/** Default {@link HandlerRegistryFactory} that wires up all production handlers. */
public class HandlerRegistryDefaultFactory implements HandlerRegistryFactory {

  private final String workerJarCommand;

  /** Required by {@link java.util.ServiceLoader}; uses {@code "in-process"} as the jar command. */
  public HandlerRegistryDefaultFactory() {
    this("in-process");
  }

  public HandlerRegistryDefaultFactory(String workerJarCommand) {
    this.workerJarCommand = workerJarCommand;
  }

  @Override
  public HandlerRegistry create() {
    return new DefaultHandlerRegistry(
        new CompareHandler(),
        new ExtractHandler(),
        new GetEnvironmentHandler(),
        new ResizeHandler(),
        new ThumbnailHandler(),
        new ScaleTransformHandler(),
        new ScaleTransformBatchHandler(),
        workerJarCommand);
  }
}
