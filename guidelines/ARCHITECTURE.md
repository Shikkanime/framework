# Architecture Guide

Respect the framework modular structure and dependency hierarchy:

```text
core (base logging & core utils)
  ├── exposed (database, HikariCP, Exposed ORM, Liquibase, AbstractRepository, Transactional)
  └── validator (reflection-based validation, custom annotations)
        └── ktor (Ktor web framework integration, RestController, routing, OpenAPI, ResponseEntity)
```

## Module Responsibilities

- **`core`**: Contains root utility classes like `LoggerFactory` and custom log formatters. Must not depend on database or web frameworks.
- **`exposed`**: Coordinates database connections via `DatabaseWrapper` (HikariCP + Exposed + Liquibase), provides `TransactionalProxy` dynamic proxies for `@Transactional` methods, and supplies `AbstractRepository` with an upsert DSL (`ifExists`, `newIfNotExists`, `applyFlush`).
- **`validator`**: Provides a runtime reflection-based validation engine (`Validator`) and validation annotations (`@RequireAtLeastOneValid`, `@NotNull`, `@NotBlank`, `@NotEmpty`).
- **`ktor`**: Bridges Ktor web server with framework annotations. Discovers `@RestController` endpoints, binds routes (`@GetMapping`, `@PostMapping`, `@PatchMapping`), resolves parameter arguments (`@QueryParam`, `@PathParam`, `@RequestBody`), executes automatic validation via `@Valid`, packages responses with `ResponseEntity`, generates OpenAPI metadata (`@Operation`, `@ApiResponses`), and handles error formatting via `MessageDto`.

## Technical Stack

This project is built with **Kotlin JVM**, **Gradle (buildSrc)**, **Ktor**, **Exposed ORM**, **HikariCP**, **Liquibase**, **Kotlin Reflect**, **Kotlinx Serialization**, **JUnit 6**, and **MockK**.
