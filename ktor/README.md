# Shikkanime Framework — Ktor Module

Web framework integration for Ktor (server **and** client): annotation-driven controllers, request
argument resolution, automatic validation, `ResponseEntity` packaging, OpenAPI documentation, a
preconfigured HTTP client, and first-class JWT authentication with refresh token rotation.

## Dependency

```kotlin
dependencies {
    implementation("fr.shikkanime.framework:ktor:<frameworkVersion>")
}
```

Or through the Gradle convention plugin (preferred for downstream projects):

```kotlin
plugins {
    id("fr.shikkanime.framework.ktor") version "<frameworkVersion>"
}
```

---

## 1. Server bootstrap

`configureDefaultModules()` installs JSON/Protobuf/CBOR content negotiation, permissive CORS, and
the built-in `GET /health` endpoint returning `OK`.

```kotlin
fun main() {
    embeddedServer(CIO, port = 8080) {
        configureDefaultModules()

        routing {
            configureSwaggerRoute("My API", "1.0-SNAPSHOT")
            ControllerBinder.register(this, listOf(MovieController()))
        }
    }.start(wait = true)
}
```

A Swagger UI is then available at `/swagger` and a generated OpenAPI document describes every
endpoint decorated with the metadata annotations.

---

## 2. Controllers and routing

Annotate a class with `@RestController(path)` and map methods with `@GetMapping`, `@PostMapping`,
or `@PatchMapping`. The controller instance is passed to `ControllerBinder.register(...)`.

```kotlin
@RestController("/api/movies")
class MovieController : IController {
    @GetMapping("/{id}")
    fun getMovie(@PathParam("id") id: Int): ResponseEntity<MovieDto> =
        ResponseEntity.ok(MovieDto(id, "Inception"))

    @PostMapping
    fun create(@RequestBody book: MovieDto): ResponseEntity<MovieDto> =
        ResponseEntity.created(book)

    @PatchMapping("/{id}")
    fun update(@PathParam("id") id: Int, @RequestBody book: MovieDto): ResponseEntity<MovieDto> =
        ResponseEntity.ok(book.copy(id = id))
}
```

### Parameter resolution

| Annotation | Source | Supported types |
|---|---|---|
| `@QueryParam("name", required)` | query string | `String`, `Int`, `Long`, `Double`, `Boolean`, enums, `Uuid` |
| `@PathParam("name", required)` | route segment | same as above |
| `@RequestBody(required)` | request payload | any serializable type (Ktor `receive`) |
| `@JwtUser` | verified JWT | `Uuid` or `String` |
| `@JwtClaims` | verified JWT | `JwtPrincipalView` |

Parameters marked `required = false` bind `null` only when the Kotlin declaration is nullable.

### Validation

Annotate a parameter with `@Valid` to run `Validator.validate(...)` on it; a
`ObjectNotValidException` becomes an HTTP 400 with an `ErrorMessageDto`.

```kotlin
@Serializable
data class CreateMovie(@NotBlank val title: String)

@PostMapping("/validated")
fun create(@RequestBody @Valid movie: CreateMovie): ResponseEntity<CreateMovie> =
    ResponseEntity.ok(movie)
```

### Responses and error payloads

Controller methods return `ResponseEntity<T>` (factories: `ok`, `created`, `badRequest`,
`notFound`, `conflict`, `internalServerError`) or a plain serializable object. Framework-generated
errors use `MessageDto.error(message)` / `ErrorMessageDto`.

```kotlin
@GetMapping("/conflict")
fun conflict(): ResponseEntity<String> =
    ResponseEntity.conflict("Movie already exists")
```

### OpenAPI metadata

```kotlin
@Operation(summary = "Get movie by ID", description = "Returns a single movie", tags = ["Movies"])
@ApiResponses([
    ApiResponse(status = 200, description = "Movie found", responseType = MovieDto::class),
    ApiResponse(status = 404, description = "Movie not found", responseType = String::class)
])
@GetMapping("/{id}")
fun getMovie(@PathParam("id") id: Int): ResponseEntity<MovieDto> =
    ResponseEntity.ok(MovieDto(id, "Inception"))
```

---

## 3. JWT authentication

The `fr.shikkanime.ktor.auth` package provides token issuance, verification, and refresh rotation
with reuse detection (RFC 8725 / RFC 9700). Security invariants: pinned algorithm, explicit
`token_type` typing, enforced issuer/audience, sanitized 401 challenges — see
[SECURITY.md](../guidelines/SECURITY.md).

### Configuration

```kotlin
val config = JwtConfig(
    algorithm = Algorithm.RSA256(publicKey, privateKey), // or Algorithm.HMAC256(secret)
    issuer = "https://auth.example.com/",
    audience = "my-api",
    accessTtl = 15.minutes,
    refreshTtl = 30.days
)
val tokenService = JwtTokenService(config)
```

The algorithm is pinned: tokens signed with any other algorithm — `alg: none` included — are
rejected. `Algorithm.none()` is rejected at configuration time.

### Installing the server provider

Call `configureJwtAuth` **before** `ControllerBinder.register`; binding a route annotated with
`@JwtAuthenticated`/`@JwtRoles` without it fails fast with an `IllegalStateException`.

```kotlin
embeddedServer(CIO, port = 8080) {
    configureDefaultModules()
    configureJwtAuth(tokenService)

    routing {
        ControllerBinder.register(this, listOf(MovieController()))
    }
}.start(wait = true)
```

### Issuing tokens (login)

```kotlin
@PostMapping("/login")
fun login(@RequestBody credentials: Credentials): ResponseEntity<TokenPair> {
    val identity = authenticate(credentials) // your authentication logic
        ?: return ResponseEntity.badRequest(MessageDto.error("Invalid credentials"))

    val accessToken = tokenService.createAccessToken(JwtIdentity(uuid = identity.uuid, roles = identity.roles))
    val refreshToken = tokenService.createRefreshToken(JwtIdentity(uuid = identity.uuid))

    refreshTokenService.register(refreshToken, userId = identity.uuid)
    return ResponseEntity.ok(TokenPair(accessToken, refreshToken))
}
```

### Protecting routes and injecting identity

```kotlin
@JwtAuthenticated
@GetMapping("/me")
fun me(@JwtUser uuid: Uuid): ResponseEntity<WhoAmI> =
    ResponseEntity.ok(WhoAmI(uuid.toString()))

@JwtAuthenticated(optional = true)
@GetMapping("/feed")
fun feed(@JwtUser uuid: Uuid?): ResponseEntity<Feed> =   // nullable: anonymous allowed
    ResponseEntity.ok(if (uuid == null) publicFeed() else personalizedFeed(uuid))

@JwtClaims
@GetMapping("/debug")
fun debug(principal: JwtPrincipalView?): String =
    principal?.claims?.toString() ?: "anonymous"

@JwtRoles("ADMIN")
@GetMapping("/admin")
fun adminOnly(): ResponseEntity<String> =
    ResponseEntity.ok("welcome admin")   // 401 without token, 403 with wrong roles
```

Rules:
- `@JwtRoles` cannot be combined with `@JwtAuthenticated(optional = true)` (rejected at startup).
- With `optional = true` and no token, only **nullable** `@JwtUser`/`@JwtClaims` parameters bind;
  required parameters abort with 401 before the method body runs.

### Refresh token rotation

Refresh tokens are single-use. Presenting a consumed token is treated as theft: the whole token
family is revoked (RFC 9700 §4.14.2). The store only ever receives SHA-256 hashes of the raw
tokens.

```kotlin
val refreshTokenService = RefreshTokenService(tokenService, InMemoryRefreshTokenStore())

@PostMapping("/refresh")
fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<TokenPair> =
    refreshTokenService.rotate(request.token)
        ?.let { ResponseEntity.ok(it) }
        ?: ResponseEntity.badRequest(MessageDto.error("Invalid refresh token"))
```

`InMemoryRefreshTokenStore` suits tests and single-instance deployments. For production, implement
`RefreshTokenStore` over your own database — `consume(tokenHash)` MUST be atomic because the
reuse-detection guarantee rests on it:

```kotlin
class PostgresRefreshTokenStore(/* ... */) : RefreshTokenStore {
    override fun store(token: RefreshTokenRecord) { /* INSERT ... */ }
    override fun consume(tokenHash: String): RefreshTokenRecord? { /* atomic UPDATE ... RETURNING */ }
    override fun revokeFamily(familyId: String) { /* UPDATE ... WHERE family_id = ? */ }
}
```

Custom claims travel with the token and survive rotation (reserved claim names are rejected):

```kotlin
tokenService.createAccessToken(
    JwtIdentity(uuid = member.uuid.toString(), roles = setOf("MEMBER"), custom = mapOf("isPrivate" to "true"))
)
```

---

## 4. HTTP client

`createHttpClient()` returns an `HttpClient` with OkHttp, request/connect/socket timeouts, and
JSON/Protobuf/CBOR content negotiation preinstalled.

```kotlin
val client = createHttpClient()

val movie: MovieDto = client.get("https://api.example.com/movies/1").body()
```

The `JwtClientInterceptor` contract (`JwtTokenStorage`) lets consumers attach the `Bearer` header
and swap refresh tokens from their own storage implementation.

---

## 5. Testing your endpoints

The `fr.shikkanime.framework:ktor-test` dependency publishes Ktor's test host so consumers write
`testApplication`-based tests without referencing Ktor directly:

```kotlin
testApplication {
    application {
        configureDefaultModules()
        configureJwtAuth(tokenService)
        routing { ControllerBinder.register(this, listOf(MovieController())) }
    }

    val response = client.get("/api/movies/1") {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }

    assertEquals(HttpStatusCode.OK, response.status)
}
```

---

## Module boundaries

- `ktor` depends on `core` and `validator`; it MUST NOT depend on `exposed`.
- `ktor-server-auth-jwt` is exposed as `api` because `JwtConfig` publicly references
  `com.auth0.jwt.algorithms.Algorithm`.
- See [AGENTS.md](AGENTS.md) for the full module rules.
