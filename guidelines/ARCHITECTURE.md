# Architecture Guide

Respect the framework modular structure and dependency hierarchy:

```text
core (base logging & core utils)
  ├── cache (two-level caching, L1 LRU + L2 Valkey binary format with CBOR, bucket versioning, single-flight loader)
  ├── exposed (database, HikariCP, Exposed ORM, Liquibase, AbstractRepository, Transactional)
  └── validator (reflection-based validation, custom annotations)
        └── ktor (Ktor web framework integration, RestController, routing, OpenAPI, ResponseEntity)
standalone dependency carriers (no source code):
koin (standalone dependency carrier: Koin runtime APIs)
ktor-test (standalone dependency carrier: Ktor server test host)
```

## Module Responsibilities

- **`core`**: Contains root utility classes like `LoggerFactory` and custom log formatters. Must not depend on database or web frameworks.
- **`cache`**: Provides a two-level caching facade (`Cache`), combining an in-memory L1 LRU cache (`L1Cache`) with a distributed L2 Valkey/Redis store (`ValkeyWrapper`), CBOR binary encoding (`BinaryCodec`), bucket versioning, and concurrent loader deduplication (`SingleFlight`).
- **`exposed`**: Coordinates database connections via `DatabaseWrapper` (HikariCP + Exposed + Liquibase), provides `TransactionalProxy` dynamic proxies for `@Transactional` methods, and supplies `AbstractRepository` with an upsert DSL (`ifExists`, `newIfNotExists`, `applyFlush`).
- **`validator`**: Provides a runtime reflection-based validation engine (`Validator`) and validation annotations (`@RequireAtLeastOneValid`, `@NotNull`, `@NotBlank`, `@NotEmpty`).
- **`ktor`**: Bridges Ktor (server **and client**) with framework conventions. Discovers `@RestController` endpoints, binds routes (`@GetMapping`, `@PostMapping`, `@PatchMapping`), resolves parameter arguments (`@QueryParam`, `@PathParam`, `@RequestBody`), executes automatic validation via `@Valid`, packages responses with `ResponseEntity`, generates OpenAPI metadata (`@Operation`, `@ApiResponses`), handles error formatting via `MessageDto`, and exposes a preconfigured HTTP client (`createHttpClient`).
- **`koin`**: Dependency carrier publishing the Koin runtime APIs (`koin-bom`, `koin-core`, `koin-annotations`) via `api(...)`; contains no source code.
- **`ktor-test`**: Dependency carrier publishing Ktor's server test host (`ktor-server-test-host`) via `api(...)` so consumers write `testApplication`-based endpoint tests without referencing Ktor directly; contains no source code.

## Dependency Sharing

The framework extends itself to consuming services: **module dependencies are exposed with `api(...)`** so that projects building on the framework can use them to their full potential (e.g. `api(libs.bundles.ktorServerEcosystem)`, `api(libs.bundles.ktorClientEcosystem)`). Use `implementation` only for dependencies that are genuinely internal to a module.

## Technical Stack

This project is built with **Kotlin JVM**, **Gradle (buildSrc)**, **Ktor**, **Exposed ORM**, **HikariCP**, **Liquibase**, **AWS Glide (Valkey/Redis)**, **Kotlin Reflect**, **Kotlinx Serialization**, **JUnit 6**, and **MockK**.
