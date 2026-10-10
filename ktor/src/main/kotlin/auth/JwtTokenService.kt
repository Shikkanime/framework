package fr.shikkanime.ktor.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import fr.shikkanime.core.LoggerFactory
import java.util.Date
import java.util.UUID
import kotlin.time.Duration

/**
 * Issues and verifies JWT access and refresh tokens.
 *
 * Security invariants (RFC 8725):
 * - The signing algorithm is pinned at construction; verification rejects tokens whose header
 *   announces any other algorithm (`alg` re-asserted on every verification as defense-in-depth).
 * - `iss`, `aud`, `exp` and `jti` are mandatory and validated on every verification.
 * - A `token_type` claim (`access` or [TokenType.REFRESH]) makes the two token kinds mutually
 *   exclusive: a refresh token can never be presented as an access token, and vice-versa.
 * - Verification failures are collapsed into [verifyAccessToken] returning `null`; no distinction
 *   between expired, tampered, or malformed is observable through the API.
 *
 * Nothing about the token contents is logged.
 *
 * @param config pinned configuration (algorithm, issuer, audience, TTLs).
 */
class JwtTokenService(internal val config: JwtConfig) {
    private val logger = LoggerFactory.getLogger()

    /**
     * Pinned verifier for access tokens, exposed for Ktor's `jwt` provider integration.
     */
    internal val accessVerifier: JWTVerifier = buildVerifier(TokenType.ACCESS)

    /**
     * Issues a short-lived access token carrying [claims].
     *
     * @param claims identity claims embedded in the token.
     * @return the signed, URL-safe compact serialization.
     */
    fun createAccessToken(claims: JwtIdentity): String =
        createToken(claims, TokenType.ACCESS, config.accessTtl)

    /**
     * Issues a long-lived refresh token carrying [claims].
     *
     * @param claims identity claims embedded in the token.
     * @return the signed, URL-safe compact serialization.
     */
    fun createRefreshToken(claims: JwtIdentity): String =
        createToken(claims, TokenType.REFRESH, config.refreshTtl)

    /**
     * Verifies [token] as an access token.
     *
     * @param token compact JWT received from the client.
     * @return the verified principal view, or `null` when the token is invalid for ANY reason.
     */
    fun verifyAccessToken(token: String): JwtPrincipalView? =
        verify(token, TokenType.ACCESS)

    /**
     * Verifies [token] as a refresh token.
     *
     * @param token compact JWT received from the client.
     * @return the verified principal view, or `null` when the token is invalid for ANY reason.
     */
    fun verifyRefreshToken(token: String): JwtPrincipalView? =
        verify(token, TokenType.REFRESH)

    private fun createToken(claims: JwtIdentity, tokenType: TokenType, ttl: Duration): String {
        val reservedCollision = claims.custom.keys intersect JwtConfig.RESERVED_CLAIMS

        require(reservedCollision.isEmpty()) { "Custom claims collide with reserved claims: $reservedCollision" }
        require(claims.uuid.isNotBlank()) { "Subject UUID cannot be blank" }

        val builder = JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withJWTId(UUID.randomUUID().toString())
            .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, tokenType.wireValue)
            .withClaim(JwtConfig.CLAIM_UUID, claims.uuid)
            .withClaim(JwtConfig.CLAIM_ROLES, claims.roles.toList())
            .withExpiresAt(Date(System.currentTimeMillis() + ttl.inWholeMilliseconds))

        claims.custom.forEach { (name, value) -> builder.withClaim(name, value) }

        return builder.sign(config.algorithm)
    }

    private fun verify(token: String, expectedType: TokenType): JwtPrincipalView? =
        try {
            val decoded = buildVerifier(expectedType).verify(token)

            if (decoded.algorithm != config.algorithm.name) {
                logger.warning("JWT rejected: algorithm mismatch")
                return null
            }

            decoded.toPrincipal()
        } catch (_: JWTVerificationException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    private fun buildVerifier(expectedType: TokenType): JWTVerifier =
        JWT.require(config.algorithm)
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaimPresence(JwtConfig.CLAIM_UUID)
            .withClaimPresence(JwtConfig.CLAIM_TOKEN_TYPE)
            .withClaimPresence("exp")
            .withClaimPresence("jti")
            .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, expectedType.wireValue)
            .build()

    /**
     * Extracts the framework-owned principal from a verified token.
     *
     * @param decoded verified token representation.
     * @return the principal with claims flattened to strings.
     */
    private fun DecodedJWT.toPrincipal(): JwtPrincipalView = JwtPrincipalView(
        subject = getClaim(JwtConfig.CLAIM_UUID).asString(),
        roles = getClaim(JwtConfig.CLAIM_ROLES).asList(String::class.java)?.toSet() ?: emptySet(),
        claims = claims.keys.associateWith { key ->
            val claim = getClaim(key)
            claim.asString() ?: claim.asList(String::class.java)?.joinToString(",")
            ?: claim.asBoolean()?.toString() ?: claim.asInt()?.toString() ?: ""
        },
        expiresAt = expiresAt.time
    )

    /**
     * Token purposes, serialized in the `token_type` claim to keep them mutually exclusive.
     */
    internal enum class TokenType(val wireValue: String) {
        ACCESS(JwtConfig.TOKEN_TYPE_ACCESS),
        REFRESH(JwtConfig.TOKEN_TYPE_REFRESH)
    }

    
}
