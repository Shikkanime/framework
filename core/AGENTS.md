# Module Rules: Core (`core`)

This file contains specific rules for the `core` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `core` module provides foundational utilities and logging capabilities for the Shikkanime framework, primarily centered around `LoggerFactory` and `LogFormatter`.

## Submodule-Specific Rules

1. **Zero Framework Dependencies**:
   - `core` MUST NOT depend on `exposed`, `validator`, `ktor`, or any other submodule.
   - Keep `core` completely decoupled so it can be safely imported by any part of the framework.

2. **Logging Architecture & Safety**:
   - `LoggerFactory` must remain thread-safe (uses internal map caching for `Logger` instances).
   - `LogFormatter` handles formatted outputs `[Timestamp] [Level] [Logger] - [Message][Throwable]`.
   - Never leak sensitive user credentials, tokens, or private data into formatted log records.

3. **Performance & Reliability**:
   - Logging calls must be lightweight to prevent runtime bottlenecks in high-throughput operations.
   - Exception stack trace formatting in `LogFormatter` must safely release system resources (`StringWriter`, `PrintWriter`).
