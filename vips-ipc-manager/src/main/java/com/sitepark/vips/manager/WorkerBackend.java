package com.sitepark.vips.manager;

import com.sitepark.vips.command.Command;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;

interface WorkerBackend extends AutoCloseable {
  void execute(Command cmd) throws IOException;

  VipsEnvironmentResponse queryEnvironment() throws IOException;

  @Override
  void close();
}
