package fr.shikkanime.ktor.auth

import com.auth0.jwt.algorithms.Algorithm
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Static configuration of the JWT issuance and verification machinery.
 *
 * @property algorithm pinned signing algorithm. RFC 8725 §3.1: verification MUST use exactly this
 * algorithm and reject any token whose header announces a different one.
 * @property issuer required `iss` claim stamped on issued tokens and asserted on verification.
 * @property audience required `aud` claim stamped on issued tokens and asserted on verification.
 * @property realm realm returned in `WWW-Authenticate` challenges.
 * @property accessTtl lifetime of access tokens; short by design (RFC 8725 §2.2 / ASVS guidance).
 * @property refreshTtl lifetime of refresh tokens; must outlive [accessTtl] by a wide margin.
 */
data class JwtConfig(
    val algorithm: Algorithm,
    val issuer: String,
    val audience: String,
    val realm: String = "Shikkanime",
    val accessTtl: Duration = 15.minutes,
    val refreshTtl: Duration = 30.days
) {
    init {
        require(algorithm.name != "none") { "The unsecured none algorithm is forbidden (RFC 8725 §3.2)" }
        require(issuer.isNotBlank()) { "Issuer cannot be blank" }
        require(audience.isNotBlank()) { "Audience cannot be blank" }
    }
    internal companion object {
        /**
         * Claim distinguishing access from refresh tokens (RFC 8725 §3.11 explicit typing).
         */
        const val CLAIM_TOKEN_TYPE = "token_type"

        /**
         * Claim carrying the subject identifier.
         */
        const val CLAIM_UUID = "uuid"

        /**
         * Claim carrying the subject roles.
         */
        const val CLAIM_ROLES = "roles"

        /**
         * Wire value of the access token type.
         */
        const val TOKEN_TYPE_ACCESS = "access"

        /**
         * Wire value of the refresh token type.
         */
        const val TOKEN_TYPE_REFRESH = "refresh"

        /**
         * Claim names the framework owns; custom claims colliding with these are rejected.
         */
        val RESERVED_CLAIMS = setOf(CLAIM_TOKEN_TYPE, CLAIM_UUID, CLAIM_ROLES, "aud", "iss", "exp", "iat", "jti")
    }
}
