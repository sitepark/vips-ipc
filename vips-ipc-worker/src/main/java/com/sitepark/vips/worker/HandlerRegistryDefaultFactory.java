package com.sitepark.vips.worker;

import com.sitepark.vips.worker.command.ConfigHandler;
import com.sitepark.vips.worker.command.GetEnvironmentHandler;
import com.sitepark.vips.worker.command.ResizeHandler;
import com.sitepark.vips.worker.command.ScaleTransformBatchHandler;
import com.sitepark.vips.worker.command.ScaleTransformHandler;
import com.sitepark.vips.worker.command.ThumbnailHandler;

/**
 * Default {@link HandlerRegistryFactory} that wires up all production handlers with a shared
 * {@link WorkerConfig}.
 */
public class HandlerRegistryDefaultFactory implements HandlerRegistryFactory {

  private final String workerJarCommand;

  public HandlerRegistryDefaultFactory(String workerJarCommand) {
    this.workerJarCommand = workerJarCommand;
  }

  @Override
  public HandlerRegistry create() {
    WorkerConfig config = new WorkerConfig();
    return new DefaultHandlerRegistry(
        new ConfigHandler(config),
        new GetEnvironmentHandler(),
        new ResizeHandler(),
        new ThumbnailHandler(),
        new ScaleTransformHandler(config),
        new ScaleTransformBatchHandler(config),
        workerJarCommand);
  }
}
