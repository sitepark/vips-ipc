# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build and verify (runs all checks)
mvn clean verify

# Run tests
mvn test

# Run a single test class
mvn test -pl vips-ipc-manager -Dtest=VipsClientTest

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

## Architecture

VIPS-IPC is a multi-module Maven project implementing inter-process communication for image processing via the VIPS library (using the FFM / Foreign Function & Memory API).

```
vips-ipc (parent)
├── vips-ipc-share   – shared DTOs between manager and worker (e.g., Resize record)
├── vips-ipc-manager – IPC client that spawns/manages the worker process
└── vips-ipc-worker  – long-running child process that does the actual VIPS image ops
```

### Communication Protocol

Manager and worker communicate over stdin/stdout using a **line-based JSON protocol**:

- **Command** (manager → worker): `{"command": "resize", "source": "...", "target": "...", "scale": 0.5}`
- **Response** (worker → manager): `{"status": "ok"}` or `{"status": "error", "message": "..."}`

### Manager (`vips-ipc-manager`)

- `VipsClient` implements `AutoCloseable`; intended for use in try-with-resources
- Delegates all command execution to a `WorkerBackend` (see below)
- **`WorkerBackend`** (interface): abstraction over the actual worker mechanism; two implementations:
  - `WorkerProcess` (default): spawns a child JVM, communicates via stdin/stdout JSON protocol.
    Uses a `ReentrantLock` to serialize commands — thread-safe, strictly sequential.
    **Auto-restart**: if the worker process dies, restarts it and retries once before throwing.
    Stderr is drained in a daemon thread. On close, sends a `Shutdown` command, waits up to
    5 seconds for clean exit, then force-kills.
  - `InProcessWorkerBackend`: dispatches commands directly to a `HandlerRegistry` in the same JVM.
    Intended for debugging when a Java debugger cannot attach to child processes.
    Subprocess settings (JAR path, JVM args, nice level, timeout) are ignored.
    Enable via `VipsClient.builder().inProcess().build()`.
- **`VipsClientPool`**: manages N `WorkerBackend` instances via `ArrayBlockingQueue`; callers borrow
  a free backend, execute the command, and return it — enabling true parallel image processing.
  Built via `VipsClient.builder().buildPool(n)`. Use `configureAll()` instead of `configure()` to
  apply encoding settings to every worker in the pool.
- **`niceLevel(int)`**: prepends `nice -n <value>` to the worker command (Linux/macOS only; silently
  ignored on Windows). Affects all threads of the worker JVM including libvips compute threads and
  codec operations.

### VIPS Parallelism Model

Understanding how libvips uses threads matters for tuning `VipsClientPool`:

- **Image computation** (resize, color transform, convolution): libvips splits the output into tiles and evaluates
  them across `VIPS_CONCURRENCY` threads (default = all CPU cores). This is the phase `VIPS_CONCURRENCY` controls.
- **Codec operations** (JPEG/PNG encode/decode via libjpeg/libpng): run largely **single-threaded**, outside the
  VIPS pipeline. Even with `VIPS_CONCURRENCY=8`, a single worker idles on CPU during these phases.

**Consequence for pool sizing:** A pool helps in all cases — while Worker 1 encodes (1 core active), Worker 2 can
run its computation phase (N cores active). For codec-heavy workloads (many small images), reduce per-worker
concurrency and increase pool size: `builder.concurrency(1).buildPool(availableProcessors())`.

### Worker (`vips-ipc-worker`)

- Entry point: `com.sitepark.vips.worker.Main`
- Reads JSON from stdin in a loop until EOF, dispatches to handlers by `"command"` field
- Calls `Vips.run(arena -> {...})` (vips-ffm arena pattern) for each image operation
- Returns one JSON response per command on stdout, flushed immediately
- Calls `Vips.shutdown()` on clean exit; exits with code 1 on fatal error

### Metadata

Every command that writes an image (`resize`, `thumbnail`, `scale-transform`,
`scale-transform-batch`) applies the same policy. It is always on; However a per-format `strip: true` still
suppresses everything.

**Written from the caller's `Metadata`** — nothing is copied from the source for these, so a field
the caller leaves `null` is simply absent from the output:

| `Metadata` field | IPTC dataset | Description      |
|------------------|--------------|------------------|
| `title`          | 5            | ObjectName       |
| `copyright`      | 116          | CopyrightNotice  |
| `description`    | 120          | Caption-Abstract |

**Copied from the source** — the hardcoded whitelist, one entry:

| Property                        | Transport |
|---------------------------------|-----------|
| `Iptc4xmpExt:DigitalSourceType` | XMP       |

`DigitalSourceType` (the AI-provenance marker) is whitelisted precisely because it has no IPTC IIM
equivalent: a rebuilt XMP packet is the only way it can survive, since the source packet is dropped.

**Dropped:** EXIF (including the embedded thumbnail), the source XMP packet, and the Photoshop
resource block. **Kept:** the ICC profile, because dropping it would shift the colours of a
wide-gamut source. libvips still writes a synthesised baseline EXIF (version, resolution,
dimensions) that carries nothing from the source.

**Orientation** is baked into the pixels with `autorot()` at load rather than preserved as a tag.
Not for want of somewhere to put it — libvips writes a synthesised baseline EXIF on JPEG save even
when `exif-data` was dropped, so the output always has an Orientation slot; it just reads 1. The
reason is geometry: a command's resize width/height, border insets and crop offsets are all in
display coordinates, while a rotated source's stored pixels are not, and `ScaleTransformSupport`
scales each axis to an exact requested size. Applying those numbers to unrotated pixels squashes the
image — a 4000×3000 source tagged "rotate 90°" asked for 300×400 would scale by 0.075 horizontally
and 0.133 vertically. Rotating first makes them agree, and matches `vips_thumbnail` (used by the
batch path), which rotates upright by default.

**Consequence:** an image processed *without* a `Metadata` carries no IPTC at all, even when the
source had some. In 2.0.0 the single-target path propagated it (the batch path already lost it).

#### Byte layout

`iptc-data` is **not** a bare IIM stream. libvips takes the whole JPEG `APP13` payload on load and
writes it back verbatim, so the blob is a Photoshop Image Resource Block:

```
"Photoshop 3.0\0"
( "8BIM" <id:2> <pascal name, padded to even> <size:4> <body> <pad to even> )*
```

The IPTC IIM stream lives in resource `0x0404` and is a sequence of datasets:

```
0x1C <record:1> <dataset:1> <len:2> <value>
```

with an extended form when the high bit of `len` is set (its low 15 bits then give the number of
following bytes holding the real length). Without the IRB the `APP13` identifier is unrecognised and
every standard reader skips the segment. The stream opens with `1:90 = ESC % G`, declaring UTF-8;
without it readers fall back to ISO-8859-1. Values over 65535 UTF-8 bytes throw
`IllegalArgumentException`.

#### Worker classes

`XmpTag` holds the copy-forward whitelist. `PhotoshopIrb` wraps the IRB container, `IptcBuilder`
writes the IIM stream, `XmpReader` / `XmpBuilder` handle the XMP packet, `ImageMetadata` the raw
libvips fields, and `MetadataPolicy` the replace-and-drop applied at each write.
`SourceMetadata.capture()` must run **before** any processing — the batch path decodes through
`writeToMemory()` / `newFromMemory()`, which strips the whole header. Production never parses IPTC;
the test-scope `IptcParser` exists so tests can assert what was written the way a real reader sees
it, rather than scanning the output for raw value bytes.

**Limitation:** libvips' `gifsave` writes neither IPTC nor XMP, so metadata is lost for GIF output.
For PNG, WebP and AVIF only the XMP packet applies — libvips writes `iptc-data` for JPEG and TIFF
only, so title/copyright/description do not reach those formats.

### Adding a New Command

Commands and responses use **sealed interfaces with Jackson polymorphic deserialization** (`@JsonTypeInfo` / `@JsonSubTypes`). To add a new command:

1. Create a record implementing `Command` in `vips-ipc-share` (e.g., `record MyOp(...) implements Command {}`)
2. Add a `@JsonSubTypes.Type(value = MyOp.class, name = "my-op")` entry to the `Command` sealed interface
3. Create a `CommandHandler<MyOp>` implementation in `vips-ipc-worker`
4. Register the handler in `DefaultHandlerRegistry`'s dispatch switch and wire it in `HandlerRegistryDefaultFactory`
5. Add a public method in `VipsClient` (manager module) to invoke it
6. Mirror the same method in `VipsClientPool` (manager module) — delegate via the pool's `execute()` helper

### Fat JAR Embedding

The manager embeds the worker's shaded fat JAR as a classpath resource. At runtime, `VipsClientBuilder` extracts it to a temp file (deleted on JVM exit) and launches it via `ProcessBuilder`. This means the manager module is self-contained — no external worker binary needed.

### Key Build Details

- **Java 25**, Google Java Format enforced via Spotless
- PMD ruleset: `pmd-ruleset.xml`; SpotBugs exclusions: `spotbug-exclude-filter.xml`
- Git hook at `.githooks/commit-msg` enforces **Conventional Commits** format
- `ci` Maven profile auto-activates when `CI` env var is set; skips Spotless apply (check still runs)
