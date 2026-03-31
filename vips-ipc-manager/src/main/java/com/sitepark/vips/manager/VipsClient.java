package com.sitepark.vips.manager;

import com.sitepark.vips.command.Resize;
import com.sitepark.vips.command.Thumbnail;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class VipsClient implements AutoCloseable {

  private final WorkerProcess workerProcess;

  public static VipsClientBuilder builder() {
    return new VipsClientBuilder();
  }

  VipsClient(List<String> command, long commandTimeoutMs) {
    this.workerProcess = new WorkerProcess(command, commandTimeoutMs);
  }

  /**
   * Scale an image by factor (0.5 = 50%).
   */
  public void resize(Path source, Path target, double scale) throws IOException {
    workerProcess.execute(
        new Resize(source.toAbsolutePath().toString(), target.toAbsolutePath().toString(), scale));
  }

  /**
   * Create a thumbnail (width in pixels, height proportional).
   */
  public void thumbnail(Path source, Path target, int width) throws IOException {
    workerProcess.execute(
        new Thumbnail(
            source.toAbsolutePath().toString(), target.toAbsolutePath().toString(), width));
  }

  @Override
  public void close() {
    workerProcess.close();
  }
}
