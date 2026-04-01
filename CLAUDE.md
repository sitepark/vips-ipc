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
- Spawns the worker via `ProcessBuilder` on first use (`ensureRunning()`)
- Uses a `ReentrantLock` to serialize commands (thread-safe)
- **Auto-restart**: if the worker process dies, `WorkerProcess` restarts it and retries the command once before throwing
- Stderr of the worker is drained in a daemon thread to prevent blocking
- On close, sends a `Shutdown` command, waits up to 5 seconds for clean exit, then force-kills

### Worker (`vips-ipc-worker`)

- Entry point: `com.sitepark.vips.worker.Main`
- Reads JSON from stdin in a loop until EOF, dispatches to handlers by `"command"` field
- Calls `Vips.run(arena -> {...})` (vips-ffm arena pattern) for each image operation
- Returns one JSON response per command on stdout, flushed immediately
- Calls `Vips.shutdown()` on clean exit; exits with code 1 on fatal error

### Adding a New Command

Commands and responses use **sealed interfaces with Jackson polymorphic deserialization** (`@JsonTypeInfo` / `@JsonSubTypes`). To add a new command:

1. Create a record implementing `Command` in `vips-ipc-share` (e.g., `record MyOp(...) implements Command {}`)
2. Add a `@JsonSubTypes.Type(value = MyOp.class, name = "my-op")` entry to the `Command` sealed interface
3. Create a `CommandHandler<MyOp>` implementation in `vips-ipc-worker`
4. Register the handler in `DefaultHandlerRegistry`'s dispatch switch and wire it in `HandlerRegistryDefaultFactory`
5. Add a public method in `VipsClient` (manager module) to invoke it

### Fat JAR Embedding

The manager embeds the worker's shaded fat JAR as a classpath resource. At runtime, `VipsClientBuilder` extracts it to a temp file (deleted on JVM exit) and launches it via `ProcessBuilder`. This means the manager module is self-contained — no external worker binary needed.

### Key Build Details

- **Java 25**, Google Java Format enforced via Spotless
- PMD ruleset: `pmd-ruleset.xml`; SpotBugs exclusions: `spotbug-exclude-filter.xml`
- Git hook at `.githooks/commit-msg` enforces **Conventional Commits** format
- `ci` Maven profile auto-activates when `CI` env var is set; skips Spotless apply (check still runs)
