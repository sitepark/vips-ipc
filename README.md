[![codecov](https://codecov.io/gh/sitepark/vips-ipc/graph/badge.svg?token=qZ7XJCXYqB)](https://codecov.io/gh/sitepark/vips-ipc)
[![Known Vulnerabilities](https://snyk.io/test/github/sitepark/vips-ipc/badge.svg)](https://snyk.io/test/github/sitepark/vips-ipc/)

# vips-ipc

Inter-process communication wrapper for [libvips](https://www.libvips.org/) image processing, designed to protect
server applications from native library crashes.

## The Problem: Native Crashes in Server Processes

libvips is a high-performance image processing library written in C. When used from a Java server application via
[vips-ffm](https://github.com/lopcode/vips-ffm) (the Foreign Function & Memory API), all native code runs inside the
same JVM process as the server.

This creates a critical reliability problem:

```mermaid
sequenceDiagram
    participant Client
    participant Server (JVM)
    participant libvips (native)

    Client->>Server (JVM): HTTP request (process image)
    Server (JVM)->>libvips (native): resize(image)
    libvips (native)--xServer (JVM): SIGSEGV / SIGABRT
    Note over Server (JVM): JVM crashes
    Note over Client: Connection lost — server is gone
```

A segmentation fault, abort signal, or heap corruption inside libvips terminates the entire JVM. Every concurrent
request fails. The server goes down. This is not a hypothetical edge case — broken input files, codec bugs, or
out-of-memory conditions in native libraries can and do trigger such crashes in production.

## The Solution: Isolation via IPC

vips-ipc moves all VIPS operations into a separate worker process. The server communicates with it over **stdin/stdout
using a line-based JSON protocol**. If the worker crashes, only that child process dies — the server process is
completely unaffected.

```mermaid
sequenceDiagram
    participant Client
    participant Server (JVM)
    participant VipsClient
    participant Worker Process (JVM)
    participant libvips (native)

    Client->>Server (JVM): HTTP request (process image)
    Server (JVM)->>VipsClient: resize(source, target, 0.5)
    VipsClient->>Worker Process (JVM): {"command":"resize",...} via stdin
    Worker Process (JVM)->>libvips (native): VImage.resize(0.5)
    libvips (native)--xWorker Process (JVM): SIGSEGV
    Note over Worker Process (JVM): Worker crashes — server is unaffected
    VipsClient->>Worker Process (JVM): Auto-restart worker
    VipsClient->>Worker Process (JVM): Retry command once
    Worker Process (JVM)-->>VipsClient: {"status":"ok"}
    VipsClient-->>Server (JVM): returns normally
    Server (JVM)-->>Client: HTTP response
```

Key properties of this design:

- **Crash isolation**: A native crash in the worker cannot propagate to the server JVM
- **Auto-restart with retry**: If the worker dies, `VipsClient` automatically spawns a new one and retries the failed
  command once before throwing an exception
- **Thread-safe**: A `ReentrantLock` serializes all commands to the worker — safe to share across threads
- **Parallel throughput**: `VipsClientPool` manages N independent worker processes for multi-image parallelism — use
  `VipsClient.builder().buildPool(n)` when processing many images concurrently
- **Self-contained**: The worker JAR is embedded inside the manager JAR as a classpath resource; no external binary or
  installation is required

## Usage

```java
try (VipsClient client = VipsClient.builder().build()) {

  // Scale an image to 50%
  client.resize(
      Path.of("/var/images/input.jpg"),
      Path.of("/var/images/output.jpg"),
      0.5);

  // Create a thumbnail with fixed width (height proportional)
  client.thumbnail(
      Path.of("/var/images/input.jpg"),
      Path.of("/var/images/thumb.jpg"),
      320);
}
```

`VipsClient` implements `AutoCloseable`. Use it in a try-with-resources block or hold a single instance for the
lifetime of your application — both patterns are supported.

### Available Methods

| Method | Description |
|--------|-------------|
| `getEnvironment()` | Returns libvips version and available image format loaders |
| `configure(jpegInterlace, strip)` | Sets global encoding parameters; `null` keeps the current value. Defaults: progressive JPEG (`true`), strip metadata (`false`) |
| `resize(source, target, scale)` | Scales an image by factor (e.g. `0.5` = 50%) |
| `thumbnail(source, target, width)` | Creates a thumbnail with fixed width; height is proportional |
| `scaleTransform(source, target, resize, border, crop, background, formats)` | Applies resize → border → crop in sequence; writes one file per requested format using `target` as base path (without extension); steps are optional (`null` to skip) |
| `scaleTransformBatch(source, targets)` | Produces multiple outputs from one source image in a single worker call; image is loaded once using shrink-on-load |

### Builder Options

```java
VipsClient client = VipsClient.builder()
    .javaExecutable("/usr/lib/jvm/java-25/bin/java")  // default: current JVM
    .jvmArgs(List.of("-Xmx512m"))                      // default: none
    .jarPath(Path.of("/opt/app/worker.jar"))            // default: embedded JAR
    .commandTimeoutMs(60_000)                           // default: 30 000 ms
    .concurrency(2)                                     // VIPS_CONCURRENCY per worker; default: 0 (all cores)
    .build();

// Pool: N independent worker processes for parallel throughput
VipsClientPool pool = VipsClient.builder()
    .concurrency(1)
    .buildPool(Runtime.getRuntime().availableProcessors());
```

## Parallel Processing

A single `VipsClient` serializes all commands to one worker process via a `ReentrantLock`. When
processing many images concurrently (e.g. from `parallelStream()`), all threads queue behind this
lock. `VipsClientPool` solves this by running N independent worker processes in parallel.

### Why a pool helps — the two-level parallelism model

libvips already parallelizes **image computation** internally: it splits the output image into tiles
and evaluates them concurrently across `VIPS_CONCURRENCY` threads (default: all CPU cores). This
speeds up resize, color conversion, sharpening, and similar operations within a single image.

However, **codec operations run outside this pipeline and are largely single-threaded**:

| Phase | Threading |
|---|---|
| JPEG decode (libjpeg) | single-threaded |
| Shrink-on-load | single-threaded |
| VIPS resize / transform | multi-threaded (`VIPS_CONCURRENCY`) |
| JPEG encode (libjpeg) | single-threaded |
| PNG encode / decode (libpng) | single-threaded |
| WebP / AVIF encode | codec-own threads (independent of `VIPS_CONCURRENCY`) |

Even with `VIPS_CONCURRENCY=8`, a single worker processes only one image at a time and leaves CPU
cores idle during encode and decode phases. A pool overlaps these phases across images:

```mermaid
sequenceDiagram
    participant Pool
    participant W1 as Worker 1
    participant W2 as Worker 2
    participant W3 as Worker 3

    Pool->>W1: Image A
    Pool->>W2: Image B
    Pool->>W3: Image C
    Note over W1: decode (1 core)
    Note over W2: decode (1 core)
    Note over W3: decode (1 core)
    Note over W1: compute (VIPS_CONCURRENCY cores)
    Note over W2: compute (VIPS_CONCURRENCY cores)
    Note over W1: encode (1 core)
    Note over W3: compute (VIPS_CONCURRENCY cores)
    W1-->>Pool: done
    W2-->>Pool: done
    W3-->>Pool: done
```

### Usage

```java
int cores = Runtime.getRuntime().availableProcessors();
try (VipsClientPool pool = VipsClient.builder().buildPool(cores)) {

  files.parallelStream().forEach(source -> {
    try {
      pool.resize(source, output(source), 0.5);
    } catch (IOException e) {
      // handle per-image error
    }
  });
}
```

`VipsClientPool` exposes the same methods as `VipsClient`. Use `configureAll()` instead of
`configure()` to apply encoding settings to all workers:

```java
pool.configureAll(/* jpegInterlace */ true, /* strip */ true);
```

### Pool sizing guidance

| Workload | Recommendation |
|---|---|
| Many small/medium images (codec-heavy) | `concurrency(1).buildPool(availableProcessors())` |
| Large images with complex transforms (compute-heavy) | `concurrency(2).buildPool(availableProcessors() / 2)` |
| Mixed or unknown | `buildPool(availableProcessors())` (default `VIPS_CONCURRENCY`) |

The rule of thumb: `pool_size × VIPS_CONCURRENCY ≈ available CPU cores`.

## How it Works

### Fat JAR Embedding

The worker is packaged as a shaded fat JAR during the Maven build. The manager module then copies that JAR into its
own classpath resources under the name `vips-ipc-worker.jar`. At runtime, `VipsClientBuilder` extracts it to a
temporary file and launches it — no separate installation or deployment step is needed.

```mermaid
graph LR
    A[vips-ipc-worker<br/>Maven Shade Plugin] -->|produces| B[vips-ipc-worker-fat.jar]
    B -->|copied into resources by<br/>maven-dependency-plugin| C[vips-ipc-manager.jar<br/>/vips-ipc-worker.jar]
    C -->|extracted to temp file<br/>at runtime| D[ /tmp/vips-ipc-worker-XXXX.jar]
    D -->|launched via<br/>ProcessBuilder| E[Worker Process]
```

### Worker Startup

When the first command is sent, `VipsClient` calls `ensureRunning()`, which:

1. Extracts the worker JAR from classpath resources to a temp file (cached for subsequent calls)
2. Spawns a child process: `java [jvmArgs] -jar /tmp/vips-ipc-worker-XXXX.jar`
3. Connects stdin/stdout pipes for the JSON protocol
4. Starts a daemon thread that continuously drains the worker's stderr (required to prevent pipe-buffer blocking)

On `close()`, the client sends a `Shutdown` command, waits up to 5 seconds for a clean exit, then force-kills the
process.

### Communication Protocol

Commands and responses are exchanged as **single JSON lines** over stdin/stdout:

```
Manager → Worker (stdin):
{"command":"resize","source":"/path/input.jpg","target":"/path/output.jpg","scale":0.5}

Worker → Manager (stdout):
{"status":"ok"}
```

On error:

```
Worker → Manager (stdout):
{"status":"error","message":"VipsError: ...", "stackTrace":"..."}
```

Both `Command` and `Response` use Jackson's `@JsonTypeInfo` polymorphic deserialization. The discriminator field
`command` selects the concrete command type; the field `status` selects the response type.

```mermaid
graph TD
    subgraph Manager Process
        VC[VipsClient] -->|serialize to JSON line| STDIN[stdin pipe]
        STDOUT[stdout pipe] -->|deserialize JSON line| VC
    end

    subgraph Worker Process
        STDIN -->|readLine| MAIN[Main loop]
        MAIN -->|dispatch| HANDLER[CommandHandler]
        HANDLER -->|Vips.run| VIPS[libvips native]
        HANDLER -->|writeValueAsString + flush| STDOUT
    end

    style VIPS fill:#ffe0b2,stroke:#e65100
```

The worker flushes stdout after every response — this is critical to prevent buffering from stalling the manager.

## Project Structure

```
vips-ipc (parent)
├── vips-ipc-share   – shared DTOs: Command/Response sealed interfaces and record implementations
├── vips-ipc-manager – VipsClient and VipsClientBuilder; manages the worker process lifecycle
└── vips-ipc-worker  – Main loop, CommandHandler implementations, libvips calls via vips-ffm
```

## Adding a New Command

1. Add a record implementing `Command` in `vips-ipc-share`:
   ```java
   public record Convert(String source, String target, String format) implements Command {}
   ```
2. Register it in the `@JsonSubTypes` annotation on the `Command` interface:
   ```java
   @JsonSubTypes.Type(value = Convert.class, name = "convert")
   ```
3. Implement `CommandHandler<Convert>` in `vips-ipc-worker`
4. Register the handler in `DefaultHandlerRegistry`'s dispatch switch and wire it in `HandlerRegistryDefaultFactory`
5. Expose a public method on `VipsClient` in `vips-ipc-manager`

## Building

```bash
# Build and verify (runs all checks)
mvn clean verify

# Run tests only
mvn test

# Fix code formatting
mvn spotless:apply

# Check formatting (fails if not compliant)
mvn spotless:check

# Static analysis
mvn spotbugs:check
mvn pmd:check

# Code coverage report
mvn jacoco:report
```

Requires Java 25 and a working libvips installation for the integration tests.
