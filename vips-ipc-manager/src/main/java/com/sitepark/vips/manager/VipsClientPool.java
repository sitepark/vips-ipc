package com.sitepark.vips.manager;

import com.sitepark.vips.command.*;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * A thread-safe pool of worker processes for parallel image processing.
 *
 * <p>Each pool entry is an independent worker subprocess. Callers borrow a free worker, execute a
 * command, and the worker is automatically returned to the pool — even if the command throws. If no
 * worker is available, the caller blocks until one is returned.
 *
 * <p>Typical usage with {@code parallelStream()}:
 *
 * <pre>{@code
 * int cores = Runtime.getRuntime().availableProcessors();
 * try (VipsClientPool pool = VipsClient.builder().buildPool(cores)) {
 *   files.parallelStream().forEach(f -> {
 *     try { pool.resize(f, output, 0.5); } catch (IOException e) { ... }
 *   });
 * }
 * }</pre>
 *
 * <p><b>Performance note:</b> {@code VIPS_CONCURRENCY} controls only the image computation phase
 * (resize, transform). Codec operations (JPEG/PNG encode/decode) are largely single-threaded
 * regardless of {@code VIPS_CONCURRENCY}. This means a pool increases throughput even with the
 * default concurrency setting, because while one worker is encoding, others can run computations in
 * parallel. For maximum throughput with many small images, combine {@code .concurrency(1)} with a
 * pool sized to the number of CPU cores.
 *
 * <p><b>Shutdown:</b> {@link #close()} shuts down all idle workers. The typical {@code
 * try-with-resources} pattern with {@code parallelStream().forEach()} is safe, because {@code
 * forEach()} blocks until all tasks complete before {@code close()} is entered.
 */
@SuppressWarnings("PMD.DoNotUseThreads")
public class VipsClientPool implements AutoCloseable {

  public static final int DEFAULT_NICE_LEVEL = 15;

  private static final Logger LOG = Logger.getLogger(VipsClientPool.class.getName());

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private static final class DefaultHolder {
    static final VipsClientPool INSTANCE;

    static {
      try {
        INSTANCE =
            VipsClient.builder()
                .niceLevel(DEFAULT_NICE_LEVEL)
                .buildPool(Runtime.getRuntime().availableProcessors());
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }

  /**
   * Returns the application-wide default pool, shared across all callers. The pool size equals
   * {@link Runtime#availableProcessors()}. The instance is created lazily on first access and never
   * replaced.
   */
  public static VipsClientPool getDefault() {
    return DefaultHolder.INSTANCE;
  }

  private final BlockingQueue<WorkerBackend> pool;

  @FunctionalInterface
  private interface WorkerAction<T> {
    T apply(WorkerBackend worker) throws IOException;
  }

  VipsClientPool(List<WorkerBackend> workers) {
    this.pool = new ArrayBlockingQueue<>(workers.size(), true);
    this.pool.addAll(workers);
  }

  // ── Core helper ───────────────────────────────────────────────

  private <T> T execute(WorkerAction<T> action) throws IOException {
    WorkerBackend worker;
    try {
      worker = pool.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for a free worker", e);
    }
    try {
      return action.apply(worker);
    } finally {
      try {
        pool.put(worker);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warning("Interrupted while returning worker to pool; pool size reduced by one");
      }
    }
  }

  // ── Public API ────────────────────────────────────────────────

  /**
   * Returns information about the libvips installation on this system, including the version and a
   * list of available image format loaders. Use this to verify prerequisites before processing
   * images.
   */
  public VipsEnvironmentResponse getEnvironment() throws IOException {
    return execute(WorkerBackend::queryEnvironment);
  }

  public <R> R execute(Command<R> command) throws IOException {
    return execute(worker -> worker.execute(command));
  }

  // ── AutoCloseable ─────────────────────────────────────────────

  /**
   * Shuts down all idle workers currently in the pool.
   *
   * <p>Workers that are currently borrowed by active threads are not affected. The typical {@code
   * try-with-resources} pattern with {@code parallelStream().forEach()} is safe because {@code
   * forEach()} blocks until all tasks complete before {@code close()} is entered.
   */
  @Override
  @SuppressWarnings("PMD.CloseResource")
  public void close() {
    List<WorkerBackend> remaining = new ArrayList<>(pool.size());
    pool.drainTo(remaining);
    for (WorkerBackend worker : remaining) {
      worker.close();
    }
  }
}
