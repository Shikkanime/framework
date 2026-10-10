# Module Rules: Ktor (`ktor`)

This file contains specific rules for the `ktor` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `ktor` module provides web framework integration for Ktor (server **and client**). It handles `@RestController` discovery, route mapping (`@GetMapping`, `@PostMapping`, `@PatchMapping`), request argument resolution (`@QueryParam`, `@PathParam`, `@RequestBody`), automatic `@Valid` validation, `ResponseEntity` packaging, `MessageDto` response serialization, and OpenAPI documentation generation, as well as framework-aware HTTP clients (e.g. a preconfigured `HttpClient`).

## Submodule-Specific Rules

1. **Module Hierarchy & Dependencies**:
   - `ktor` depends on `core` and `validator`. It MUST NOT depend on `exposed`.

2. **Built-in Health Endpoint**:
   - `HealthController` (in `ktor/src/main/kotlin/HealthController.kt`) is auto-registered by
     `configureDefaultModules()` and exposes `GET /health` returning `OK`: every consumer gets the
     same UP endpoint for free. Consumers must NOT re-declare their own `/health` controller (it
     would be redundant).

3. **Controller & Route Binding**:
   - Controllers must be annotated with `@RestController(path = "...")`.
   - Controller methods mapped with `@GetMapping`, `@PostMapping`, or `@PatchMapping` must define clean route contracts.

4. **Argument Resolution & Validation**:
   - Parameters resolved via `@QueryParam`, `@PathParam`, or `@RequestBody` must perform safe type conversions (supporting String, Int, Long, Double, Boolean, Enums, Uuid).
   - When a parameter is annotated with `@Valid`, `ControllerBinder` must execute `Validator.validate(value)` and translate any `ObjectNotValidException` into an HTTP 400 response with an `ErrorMessageDto`.
   - Parameters resolved via `@JwtUser` (subject `Uuid`/`String`) or `@JwtClaims` ([JwtPrincipalView]) follow the same nullability contract as `@QueryParam`: with `@JwtAuthenticated(optional = true)` and no token, only nullable parameters bind; required parameters abort with HTTP 401 (`JwtPrincipalMissingException`).

4b. **JWT Authentication (`fr.shikkanime.ktor.auth`)**:
   - `JwtTokenService` issues and verifies access/refresh tokens with a PINNED algorithm (RFC 8725 §3.1): verification rejects any token whose header announces another algorithm, `alg: none` included.
   - A `token_type` claim (`access`/`refresh`) keeps the two kinds mutually exclusive (RFC 8725 §3.11 explicit typing); a refresh token can never authenticate a protected route.
   - `JwtConfig` carries issuer/audience (both enforced), TTLs (access 15 min by design), and the realm.
   - Refresh rotation: `RefreshTokenService.rotate` consumes the presented token atomically through the consumer-implemented `RefreshTokenStore` SPI; a replay revokes the whole token family (RFC 9700 §4.14.2). The store only ever sees SHA-256 hashes.
   - `@JwtAuthenticated(optional)` gates routes, `@JwtRoles(...)` adds a 403 role check, `@JwtUser`/`@JwtClaims` inject verified identity.
   - Fail-fast: binding a controller with JWT annotations without calling `configureJwtAuth` throws `IllegalStateException` at startup.
   - 401 challenges are sanitized and identical for expired/tampered/malformed tokens — never leak the cause.

5. **Standard Responses & OpenAPI Metadata**:
   - Methods should return responses wrapped in `ResponseEntity<T>` or direct serializable objects.
   - Error payloads must utilize `MessageDto.error(message)` or `ErrorMessageDto`.
   - Document endpoints with `@Operation`, `@ApiResponses`, and `@ApiResponse` to generate compliant OpenAPI documentation.
