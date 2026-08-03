# API and Ktor Conventions (`ktor` module)

## 1. Controller & Route Declarations

Controllers are annotated with `@RestController(path = "/prefix")` and route functions with `@GetMapping`, `@PostMapping`, or `@PatchMapping`.

```kotlin
@RestController("/api/v1/users")
class UserController {
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user by their unique identifier")
    @ApiResponses([
        ApiResponse(200, "User found", UserDto::class),
        ApiResponse(404, "User not found", ErrorMessageDto::class)
    ])
    fun getUser(@PathParam("id") id: Int): ResponseEntity<UserDto> =
        ResponseEntity.ok(...)
}
```

## 2. Parameter Resolution & Validation

- Use `@QueryParam("name")` for query string values.
- Use `@PathParam("name")` for route path segments.
- Use `@RequestBody` for JSON payload deserialization.
- Add `@Valid` on parameters to automatically validate the deserialized object via `Validator.validate(...)`.

## 3. Standardized Response Payloads

- Use `ResponseEntity.ok(body)`, `ResponseEntity.created(body)`, `ResponseEntity.badRequest(body)`, etc.
- Return `MessageDto` / `ErrorMessageDto` for error responses:
```kotlin
MessageDto.error("Invalid parameter")
```
