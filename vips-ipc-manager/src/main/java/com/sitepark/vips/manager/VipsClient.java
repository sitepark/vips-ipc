package com.sitepark.vips.manager;

import com.sitepark.vips.command.*;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;

public class VipsClient implements AutoCloseable {

  private final WorkerBackend backend;

  public static VipsClientBuilder builder() {
    return new VipsClientBuilder();
  }

  VipsClient(WorkerBackend backend) {
    this.backend = backend;
  }

  /**
   * Returns information about the libvips installation on this system, including the version and a
   * list of available image format loaders. Use this to verify prerequisites before processing
   * images.
   */
  public VipsEnvironmentResponse getEnvironment() throws IOException {
    return backend.queryEnvironment();
  }

  public <R> R execute(Command<R> command) throws IOException {
    return backend.execute(command);
  }

  @Override
  public void close() {
    backend.close();
  }
}
