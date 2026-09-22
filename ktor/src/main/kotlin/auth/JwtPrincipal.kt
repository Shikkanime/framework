package fr.shikkanime.ktor.auth

/**
 * Verified view of a JWT, deliberately free of third-party token types.
 *
 * Consumers never touch the underlying library representation: everything a route needs is
 * exposed here, keeping the com.auth0 API out of downstream projects.
 *
 * @property subject value of the `uuid` claim.
 * @property roles role names carried by the `roles` claim.
 * @property claims every registered claim as a string (arrays are joined, for display only).
 * @property expiresAt token expiration instant in epoch milliseconds.
 */
data class JwtPrincipalView(
    val subject: String,
    val roles: Set<String>,
    val claims: Map<String, String>,
    val expiresAt: Long
)
