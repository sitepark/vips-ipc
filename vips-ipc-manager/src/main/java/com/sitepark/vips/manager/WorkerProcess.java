package com.sitepark.vips.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitepark.vips.command.Command;
import com.sitepark.vips.command.GetEnvironment;
import com.sitepark.vips.command.Shutdown;
import com.sitepark.vips.response.ErrorResponse;
import com.sitepark.vips.response.OkResponse;
import com.sitepark.vips.response.Response;
import com.sitepark.vips.response.VipsEnvironmentResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({
  "PMD.DoNotUseThreads",
  "PMD.AvoidCatchingGenericException",
  "PMD.NullAssignment",
  "PMD.AssignmentInOperand",
  "PMD.GuardLogStatement",
  "PMD.AvoidUsingVolatile",
  "PMD.ExceptionAsFlowControl",
  "PMD.CyclomaticComplexity",
  "PMD.CognitiveComplexity"
})
class WorkerProcess implements WorkerBackend {

  private static final Logger LOG = Logger.getLogger(WorkerProcess.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final List<String> command;
  private final long commandTimeoutMs;
  private final int concurrency;
  private final ReentrantLock lock = new ReentrantLock();
  private final ExecutorService ioReader =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "vips-worker-io-reader");
            t.setDaemon(true);
            return t;
          });
  private volatile boolean closed;

  private Process process;
  private BufferedWriter toWorker;
  private BufferedReader fromWorker;

  WorkerProcess(List<String> command, long commandTimeoutMs, int concurrency) {
    this.command = List.copyOf(command);
    this.commandTimeoutMs = commandTimeoutMs;
    this.concurrency = concurrency;
  }

  // ── Public API ─────────────────────────────────────────────

  @Override
  public void execute(Command cmd) throws IOException {
    lock.lock();
    try {
      switch (sendCommand(cmd, true)) {
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
      return switch (sendCommand(new GetEnvironment(), true)) {
        case VipsEnvironmentResponse r -> r;
        case ErrorResponse err ->
            throw new IOException("Vips worker error: " + err.message() + "\n" + err.stackTrace());
        default -> throw new IOException("Unexpected response type");
      };
    } finally {
      lock.unlock();
    }
  }

  // ── Worker Communication ───────────────────────────────────

  private Response sendCommand(Command cmd, boolean canRetry) throws IOException {
    ensureRunning();

    try {
      String json = MAPPER.writeValueAsString(cmd);
      toWorker.write(json);
      toWorker.newLine();
      toWorker.flush();

      Future<String> future = ioReader.submit(fromWorker::readLine);
      String responseLine;
      try {
        responseLine = future.get(commandTimeoutMs, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        future.cancel(true);
        LOG.warning("Timeout waiting for worker response after " + commandTimeoutMs + " ms");
        destroyProcess();
        if (canRetry && !closed) {
          LOG.info("Restarting worker process and retrying command …");
          return sendCommand(cmd, false);
        }
        throw new IOException("Worker process timeout after " + commandTimeoutMs + " ms", e);
      } catch (ExecutionException e) {
        throw new IOException("I/O error reading worker response", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for worker response", e);
      }

      if (responseLine == null) {
        LOG.warning("Worker process terminated unexpectedly (exit code: " + getExitCode() + ")");
        destroyProcess();

        if (canRetry) {
          LOG.info("Restarting worker process and retrying command …");
          return sendCommand(cmd, false);
        }
        throw new IOException("Worker process crashed and retry failed");
      }

      return MAPPER.readValue(responseLine, Response.class);

    } catch (IOException e) {
      destroyProcess();

      if (canRetry && !closed) {
        LOG.log(Level.WARNING, "I/O error, restarting worker …", e);
        return sendCommand(cmd, false);
      }
      throw e;
    }
  }

  // ── Process Lifecycle ──────────────────────────────────────

  private void ensureRunning() throws IOException {
    if (closed) {
      throw new IllegalStateException("WorkerProcess is already closed");
    }
    if (process == null || !process.isAlive()) {
      startProcess();
    }
  }

  private void startProcess() throws IOException {
    LOG.info("Starting worker process: " + String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(false);
    if (concurrency > 0) {
      pb.environment().put("VIPS_CONCURRENCY", String.valueOf(concurrency));
    }
    Process started = pb.start();
    try {
      toWorker =
          new BufferedWriter(
              new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
      fromWorker =
          new BufferedReader(
              new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
    } catch (Exception e) {
      started.destroyForcibly();
      throw e;
    }
    process = started;

    LOG.info("Worker process PID: " + process.pid());

    startStderrConsumer(process.getErrorStream());
  }

  private void startStderrConsumer(InputStream stderr) {
    Thread t =
        new Thread(
            () -> {
              try (var reader =
                  new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  LOG.warning("[vips-worker stderr] " + line);
                }
              } catch (IOException ignored) {
                // process has ended
              }
            },
            "vips-worker-stderr");
    t.setDaemon(true);
    t.start();
  }

  @SuppressFBWarnings("DE_MIGHT_IGNORE")
  private void destroyProcess() {
    if (process != null) {
      try {
        toWorker.close();
      } catch (Exception ignored) {
      }
      try {
        fromWorker.close();
      } catch (Exception ignored) {
      }
      process.destroyForcibly();
      process = null;
    }
  }

  private String getExitCode() {
    try {
      return process != null && !process.isAlive() ? String.valueOf(process.exitValue()) : "?";
    } catch (Exception e) {
      return "?";
    }
  }

  // ── AutoCloseable ──────────────────────────────────────────────────────────

  @Override
  @SuppressFBWarnings("DE_MIGHT_IGNORE")
  @SuppressWarnings("PMD.EmptyCatchBlock")
  public void close() {
    lock.lock();
    try {
      if (process != null && process.isAlive()) {
        // Send Shutdown command so the worker exits cleanly after processing any in-flight command.
        // Fall back to closing stdin if the send fails (e.g. worker already dead).
        try {
          sendCommand(new Shutdown(), false);
        } catch (Exception ignored) {
          try {
            toWorker.close();
          } catch (Exception alsoIgnored) {
          }
        }

        closed = true;

        try {
          if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
          }
        } catch (InterruptedException ignored) {
          process.destroyForcibly();
          Thread.currentThread().interrupt();
        }
      } else {
        closed = true;
      }
    } finally {
      lock.unlock();
    }
    ioReader.shutdown();
  }
}
