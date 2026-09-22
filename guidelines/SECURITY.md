# Security Guide

- **Validate all inputs**: Controller inputs must be validated using `@Valid` and `Validator`.
- **Sanitize Exception Output**: Never return raw exception tracebacks or SQL syntax errors to clients. Convert errors to `ErrorMessageDto`.
- **Log Hygiene**: `LoggerFactory` must never format or log passwords, authentication tokens, API keys, or personal identifiers.
- **Reflection Boundaries**: In `validator` and `ktor`, invoke reflection safely (`KProperty.call`, `Method.invoke`) handling potential access or invocation exceptions gracefully.

## JWT Authentication (`fr.shikkanime.ktor.auth`)

- **Pin the algorithm (RFC 8725 §3.1)**: the verifier accepts exactly the `JwtConfig.algorithm`; tokens announcing `alg: none` or a mismatched algorithm are rejected. Never dispatch on the token header.
- **Explicit token typing (RFC 8725 §3.11)**: the `token_type` claim keeps access and refresh tokens mutually exclusive — a refresh token can never authenticate a protected route.
- **Enforce issuer and audience**: both claims are stamped at issuance and asserted at verification.
- **Short-lived access tokens**: the default access TTL is 15 minutes; never extend it for convenience.
- **Refresh rotation with reuse detection (RFC 9700 §4.14.2)**: every refresh token is single-use; presenting a consumed token revokes its entire family. The `RefreshTokenStore` SPI consumes atomically — implementers MUST preserve that guarantee.
- **Hash refresh tokens at rest**: the store only ever receives SHA-256 digests; a store leak must not yield usable credentials.
- **Sanitized failures**: authentication challenges are identical for expired, tampered, and malformed tokens — no oracle. The framework never logs token contents.
- **Fail fast at startup**: binding a controller method annotated `@JwtAuthenticated`/`@JwtRoles` without calling `configureJwtAuth` throws `IllegalStateException` rather than registering a silently unprotected route.
