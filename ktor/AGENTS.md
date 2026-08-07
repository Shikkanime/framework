# Module Rules: Ktor (`ktor`)

This file contains specific rules for the `ktor` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `ktor` module provides web framework integration for Ktor (server **and client**). It handles `@RestController` discovery, route mapping (`@GetMapping`, `@PostMapping`, `@PatchMapping`), request argument resolution (`@QueryParam`, `@PathParam`, `@RequestBody`), automatic `@Valid` validation, `ResponseEntity` packaging, `MessageDto` response serialization, and OpenAPI documentation generation, as well as framework-aware HTTP clients (e.g. a preconfigured `HttpClient`).

## Submodule-Specific Rules (Règles du sous-module)

1. **Module Hierarchy & Dependencies**:
   - `ktor` depends on `core` and `validator`. It MUST NOT depend on `exposed`.

2. **Controller & Route Binding**:
   - Controllers must be annotated with `@RestController(path = "...")`.
   - Controller methods mapped with `@GetMapping`, `@PostMapping`, or `@PatchMapping` must define clean route contracts.

3. **Argument Resolution & Validation**:
   - Parameters resolved via `@QueryParam`, `@PathParam`, or `@RequestBody` must perform safe type conversions (supporting String, Int, Long, Double, Boolean, Enums, Uuid).
   - When a parameter is annotated with `@Valid`, `ControllerBinder` must execute `Validator.validate(value)` and translate any `ObjectNotValidException` into an HTTP 400 response with an `ErrorMessageDto`.

4. **Standard Responses & OpenAPI Metadata**:
   - Methods should return responses wrapped in `ResponseEntity<T>` or direct serializable objects.
   - Error payloads must utilize `MessageDto.error(message)` or `ErrorMessageDto`.
   - Document endpoints with `@Operation`, `@ApiResponses`, and `@ApiResponse` to generate compliant OpenAPI documentation.
