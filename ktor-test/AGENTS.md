# Module Rules: Ktor Test (`ktor-test`)

This file contains specific rules for the `ktor-test` submodule. All agents working within this
submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `ktor-test` module is a **dependency carrier**: it publishes Ktor's server test host
(`io.ktor:ktor-server-test-host`) to downstream projects via `api(...)`, so consumers can write
`testApplication`-based endpoint tests without referencing Ktor directly.

## Submodule-Specific Rules

1. **No source code**:
   - The `ktor-test` module MUST contain **no Kotlin/Java source** (`src/` directory stays absent).
   - It only exposes Ktor test dependencies with `api(...)` so consumers get them transitively.
   - Never add application code, helpers, or wrappers here.

2. **Dependency exposure**:
   - Ktor versions are centralized in `gradle/libs.versions.toml` (`ktorServerTestHost`,
     `ktorClientMock`).
   - Dependencies are exposed with `api(...)` (Dependency Sharing rule), never `implementation`.

3. **Consumption**:
   - Unlike the Koin compiler plugin, there is **no Gradle plugin** for this module. Consumers
     declare it explicitly in their test scope:
     `testImplementation("fr.shikkanime.framework:ktor-test:<frameworkVersion>")`.
   - `testImplementation`-only consumption keeps the test host and the mock client engine
     **off the consumer's production classpath** while centralizing the versions in the
     framework catalog.
