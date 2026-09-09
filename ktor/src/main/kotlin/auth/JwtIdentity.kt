package fr.shikkanime.ktor.auth

/**
 * Identity carried by a JWT issued by [JwtTokenService].
 *
 * Framework-agnostic model: the consumer maps its own user entities to this identity when tokens
 * are issued and reads it back after verification.
 *
 * @property uuid unique, stable identifier of the authenticated subject.
 * @property roles role names carried by the subject, checked by [JwtRoles].
 * @property custom additional application claims, never interpreted by the framework.
 */
data class JwtIdentity(
    val uuid: String,
    val roles: Set<String> = emptySet(),
    val custom: Map<String, String> = emptyMap()
)
