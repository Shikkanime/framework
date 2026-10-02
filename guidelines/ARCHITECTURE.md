# Architecture Guide

Respect the framework modular structure and dependency hierarchy:

```text
core (base logging & core utils)
  ├── cache (two-level caching, L1 LRU + L2 Valkey binary format with CBOR, bucket versioning, single-flight loader)
  ├── exposed (database, HikariCP, Exposed ORM, Liquibase, AbstractRepository, Transactional)
  └── validator (reflection-based validation, custom annotations)
        └── ktor (Ktor web framework integration, RestController, routing, OpenAPI, ResponseEntity)
└── graphql (GraphQL engine, /graphql route, endpoint security stack, cost model, DataLoader orchestration)
standalone dependency carriers (no source code):
koin (standalone dependency carrier: Koin runtime APIs)
  └── koin-exposed (Koin bridge for transactions: applyTransactionalProxies post-processor wrapping @Transactional services in TransactionalProxy)
ktor-test (standalone dependency carrier: Ktor server test host)
```

## Module Responsibilities

- **`core`**: Contains root utility classes like `LoggerFactory` and custom log formatters. Must not depend on database or web frameworks.
- **`cache`**: Provides a two-level caching facade (`Cache`), combining an in-memory L1 LRU cache (`L1Cache`) with a distributed L2 Valkey/Redis store (`ValkeyWrapper`), CBOR binary encoding (`BinaryCodec`), bucket versioning, and concurrent loader deduplication (`SingleFlight`).
- **`exposed`**: Coordinates database connections via `DatabaseWrapper` (HikariCP + Exposed + Liquibase), provides `TransactionalProxy` dynamic proxies for `@Transactional` methods, and supplies `AbstractRepository` with an upsert DSL (`ifExists`, `newIfNotExists`, `applyFlush`).
- **`validator`**: Provides a runtime reflection-based validation engine (`Validator`) and validation annotations (`@RequireAtLeastOneValid`, `@NotNull`, `@NotBlank`, `@NotEmpty`).
- **`ktor`**: Bridges Ktor (server **and client**) with framework conventions. Discovers `@RestController` endpoints, binds routes (`@GetMapping`, `@PostMapping`, `@PatchMapping`), resolves parameter arguments (`@QueryParam`, `@PathParam`, `@RequestBody`), executes automatic validation via `@Valid`, packages responses with `ResponseEntity`, generates OpenAPI metadata (`@Operation`, `@ApiResponses`), handles error formatting via `MessageDto`, and exposes a preconfigured HTTP client (`createHttpClient`).
- **`graphql`**: Serves the same use cases as REST over a GraphQL transport (`graphql-kotlin` on `graphql-java`). Owns the engine wiring, the `/graphql` route, the security stack guarding it, the static cost estimator, the document bounds, and the orchestration of consumer-provided DataLoaders. Does not depend on `exposed`.
- **`koin`**: Dependency carrier publishing the Koin runtime APIs (`koin-bom`, `koin-core`, `koin-annotations`) via `api(...)`; contains no source code.
- **`koin-exposed`**: Bridges Koin with the `exposed` transaction model. Provides `applyTransactionalProxies()`, a runtime post-processor replacing eligible Koin singleton definitions with `TransactionalProxy`-wrapping factories so consumers never declare proxy bindings manually. Relies on `@KoinInternalApi` registry access; revisit on Koin upgrades.
- **`ktor-test`**: Dependency carrier publishing Ktor's server test host (`ktor-server-test-host`) via `api(...)` so consumers write `testApplication`-based endpoint tests without referencing Ktor directly; contains no source code.

## Dependency Sharing

The framework extends itself to consuming services: **module dependencies are exposed with `api(...)`** so that projects building on the framework can use them to their full potential (e.g. `api(libs.bundles.ktorServerEcosystem)`, `api(libs.bundles.ktorClientEcosystem)`). Use `implementation` only for dependencies that are genuinely internal to a module.

## Running Both Transports Over One DTO

A DTO used by REST **and** GraphQL does not need a transport-specific shape. The rule that makes it
work is what the field is declared as, because graphql-kotlin resolves a field differently depending
on it:

- a **property** is read on every request, whatever the client asked for;
- a **function** is called only when the client selects it.

So a plain property is always loaded, and a relation that is expensive to load belongs in a function
that resolves it lazily. That is what keeps the same type cheap on both transports: REST serialises
everything, GraphQL serialises the selection, and neither pays for what was not asked.

The type itself stays transport-agnostic: no `GraphQL` annotation is needed on a DTO that REST also
serves, and none should be added, or REST starts depending on the GraphQL library.

## Technical Stack

This project is built with **Kotlin JVM**, **Gradle (buildSrc)**, **Ktor**, **Exposed ORM**, **HikariCP**, **Liquibase**, **AWS Glide (Valkey/Redis)**, **Kotlin Reflect**, **Kotlinx Serialization**, **JUnit 6**, and **MockK**.
