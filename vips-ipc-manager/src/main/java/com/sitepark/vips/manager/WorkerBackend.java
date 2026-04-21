package com.sitepark.vips.manager;

import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.Result;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;

interface WorkerBackend extends AutoCloseable {
  Result execute(Command cmd) throws IOException;

  VipsEnvironmentResponse queryEnvironment() throws IOException;

  @Override
  void close();
}
