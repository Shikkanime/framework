# LLM Agent Instructions - Shikkanime Framework

As an AI agent, your primary directive is to adhere to the established patterns, API contracts, and modular architecture of **Shikkanime Framework**. Your goal is to write clean, maintainable, and secure framework components in Kotlin that align with the existing codebase.

This file contains your core, non-negotiable root rules (règles mère). For detailed framework implementation guidance, refer to the linked documents in the `guidelines` directory as well as submodule-specific rules in each subfolder (`core/AGENTS.md`, `cache/AGENTS.md`, `exposed/AGENTS.md`, `validator/AGENTS.md`, `ktor/AGENTS.md`).

## 1. The Prime Directive: Respect Framework Architecture & Module Boundaries

The project is structured into distinct, decoupled framework modules. **Do not violate module boundaries or introduce circular dependencies.**

- **Dependencies Flow:** `core` (standalone) <- `cache`, `exposed` & `validator` <- `ktor`
- **`core`**: Base logging (`LoggerFactory`), utilities. MUST remain zero-dependency relative to other submodules.
- **`cache`**: Two-level caching engine (`Cache`), L1 LRU memory cache (`L1Cache`), L2 Valkey client (`ValkeyWrapper`), CBOR binary serialization (`BinaryCodec`), bucket versioning, and single-flight loader deduplication (`SingleFlight`).
- **`exposed`**: Database connection management (`DatabaseWrapper`), Exposed ORM integration, Liquibase migrations, `@Transactional` annotations & proxying (`TransactionalProxy`), and repository base classes (`AbstractRepository`).
- **`validator`**: Reflection-based validation engine (`Validator`), constraints (`@RequireAtLeastOneValid`, `@NotNull`, `@NotBlank`, `@NotEmpty`), and `ObjectNotValidException`.
- **`ktor`**: Ktor web integrations, route annotation binding (`@RestController`, `@GetMapping`, `@PostMapping`, `@PatchMapping`), request parameter resolvers (`@QueryParam`, `@PathParam`, `@RequestBody`), automatic `@Valid` validation integration, `ResponseEntity` wrapper, `MessageDto` error response standardization, and OpenAPI metadata (`@Operation`, `@ApiResponses`, `@ApiResponse`).

For detailed architectural principles, read the [Architecture Guide](guidelines/ARCHITECTURE.md).

## 2. Security & Reflection Safety

- Treat all external inputs, HTTP request bodies, path/query params, and reflection targets as untrusted.
- Validate incoming data thoroughly before processing using `@Valid` and `Validator`.
- **Never** expose sensitive internal details (e.g. database exceptions, stack traces) in public REST responses. Use standardized error payloads like `ErrorMessageDto`.
- **Never** log sensitive information such as credentials, secrets, tokens, or personal identifiers.

Consult the [Security Guide](guidelines/SECURITY.md) for detailed instructions.

## 3. Write Clean Kotlin Code for Humans and AI

- Write all code, comments, KDoc documentation, and test cases in **English**.
- Use explicit types for public framework APIs.
- Prefer immutability (`val`, immutable collections, data classes).
- Reuse existing framework annotations, patterns, and conventions.

Refer to the style, convention, and testing guides:
- [Code Style Guide](guidelines/CODE_STYLE.md)
- [Kotlin Conventions](guidelines/KOTLIN_CONVENTIONS.md)
- [API Conventions](guidelines/API_CONVENTIONS.md)
- [Cache Guide](guidelines/CACHE.md)
- [Testing Guide](guidelines/TESTING.md)

## 4. Submodule Rules (Règles par Sous-Module)

Each framework module has its own dedicated `AGENTS.md` specifying module-specific constraints and responsibilities:
- [`core/AGENTS.md`](core/AGENTS.md): Core utilities & custom logging system.
- [`cache/AGENTS.md`](cache/AGENTS.md): Two-level caching facade, Valkey wrapper, CBOR codec, and single-flight deduplication.
- [`exposed/AGENTS.md`](exposed/AGENTS.md): Database wrappers, transaction management, Liquibase, and repository upsert DSL.
- [`validator/AGENTS.md`](validator/AGENTS.md): Annotation-based reflection validator and custom constraints.
- [`ktor/AGENTS.md`](ktor/AGENTS.md): Controller binding, HTTP argument resolution, response wrappers, and OpenAPI doc generation.

## 5. Before Submitting Changes

Before concluding your task, perform a final check:
- Are module dependency boundaries respected?
- Is public framework code well-documented with KDoc?
- Are database calls enclosed in transactions when required?
- Is validation logic tested and error responses sanitized?
- Have you verified the build and tests with `./gradlew test`?

Consult additional guides for [Cache](guidelines/CACHE.md), [Database](guidelines/DATABASE.md), and [Performance](guidelines/PERFORMANCE.md) rules as needed.
