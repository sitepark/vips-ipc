package com.sitepark.vips.manager;

import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.handler.HandlerRegistry;
import com.sitepark.vips.handler.HandlerRegistryFactory;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

@SuppressWarnings("PMD.GuardLogStatement")
class InProcessWorkerBackend implements WorkerBackend {

  private static final Logger LOG = Logger.getLogger(InProcessWorkerBackend.class.getName());

  private final HandlerRegistry registry;
  private final ReentrantLock lock = new ReentrantLock();

  InProcessWorkerBackend(HandlerRegistryFactory factory) {
    this.registry = factory.create();
  }

  @Override
  public void execute(Command cmd) throws IOException {
    lock.lock();
    try {
      switch (registry.dispatch(cmd)) {
        case OkResponse ok -> {
          if (ok.debug() != null) {
            LOG.info("Worker cli-command:\n" + ok.debug().cliCommand());
          }
        }
        case ErrorResponse err ->
            throw new IOException("Vips worker error: " + err.message() + "\n" + err.stackTrace());
        default -> throw new IOException("Unexpected response type");
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public VipsEnvironmentResponse queryEnvironment() throws IOException {
    lock.lock();
    try {
      return switch (registry.dispatch(new GetEnvironment())) {
        case VipsEnvironmentResponse r -> r;
        case ErrorResponse err ->
            throw new IOException("Vips worker error: " + err.message() + "\n" + err.stackTrace());
        default -> throw new IOException("Unexpected response type");
      };
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    // No-op: libvips lifecycle is managed by the parent JVM.
    // Vips.shutdown() is intentionally NOT called here — in the subprocess model that
    // is called by Main.java's finally block in the child JVM, not by WorkerProcess.close().
  }
}
