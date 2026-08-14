# Module Rules: Koin (`koin`)

This file contains specific rules for the `koin` submodule. All agents working within this submodule
must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `koin` module is a **dependency carrier**: it publishes the Koin runtime APIs
(`koin-bom`, `koin-core`, `koin-annotations`) to downstream projects via `api(...)`.

## Submodule-Specific Rules

1. **No source code**:
   - The `koin` module MUST contain **no Kotlin/Java source** (`src/` directory stays absent).
   - It only exposes Koin dependencies with `api(...)` so consumers get them transitively.
   - Never add application code, helpers, or wrappers here.

2. **Dependency exposure**:
   - Koin versions are centralized in `gradle/libs.versions.toml` (`koin`, `koinCompiler`).
   - Dependencies are exposed with `api(...)` (Dependency Sharing rule), never `implementation`.

3. **Consumption**:
   - The Koin **compiler plugin** is applied to consumers via the Gradle plugin
     `fr.shikkanime.framework.koin` (see `plugin/`), not by this module.
