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
- Type a parameter with its real domain type, never with `String` to re-parse it:
  `@PathParam("id") id: Uuid`.
- A non-nullable parameter whose value does not match its type is refused with an HTTP 400 carrying
  a `MessageDto` error (`InvalidParameterException`). A nullable parameter is optional by nature: an
  absent or unconvertible value resolves to `null` and never refuses the request.

## 3. Standardized Response Payloads

- Use `ResponseEntity.ok(body)`, `ResponseEntity.created(body)`, `ResponseEntity.badRequest(body)`, etc.
- Return `MessageDto` / `ErrorMessageDto` for error responses:
```kotlin
MessageDto.error("Invalid parameter")
```
