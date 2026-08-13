# Module Rules: Plugin (`plugin`)

This file contains specific rules for the `plugin` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `plugin` module provides Gradle convention plugins for downstream projects consuming **Shikkanime Framework**. It encapsulates plugin applications and version alignment (e.g., Ktor plugin integration via `fr.shikkanime.framework.ktor`).

## Submodule-Specific Rules

1. **Gradle Plugin API**:
   - All convention plugins must implement `org.gradle.api.Plugin<Project>`.
   - Document every public plugin class with comprehensive KDoc comments.
   - Ensure plugins handle property fallbacks gracefully.

2. **Testing**:
   - Every plugin must have corresponding unit tests verifying plugin application and configuration behavior using Gradle's `ProjectBuilder`.
