package fr.shikkanime.ktor.auth

import io.ktor.server.auth.jwt.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

/**
 * JWT-aware hooks of the controller binding machinery.
 */

/**
 * Checks that annotated methods can actually authenticate at request time.
 *
 * Called by `ControllerBinder.register` for every bound controller method; fails fast at startup
 * rather than registering a route that would only fail per-request.
 *
 * @param function controller method about to be bound.
 * @throws IllegalStateException when [function] requires JWT auth but `configureJwtAuth` was
 * never called on the application.
 */
internal fun requireJwtProvider(function: KFunction<*>) {
    val requiresAuth = function.hasAnnotation<JwtAuthenticated>() || function.findAnnotation<JwtRoles>() != null

    if (requiresAuth && !jwtProviderConfigured)
        throw IllegalStateException(
            "Method ${function.name} is annotated with @JwtAuthenticated or @JwtRoles but " +
                    "configureJwtAuth(application, tokenService) was never called"
        )
}

/**
 * Application-level flag flipped by [configureJwtAuth]; read by the fail-fast binding check.
 *
 * Reset by [resetJwtProviderFlag] so isolated test applications start from a clean state.
 */
internal var jwtProviderConfigured: Boolean = false

/**
 * Arms the fail-fast binding check; called by [configureJwtAuth].
 */
internal fun markJwtProviderConfigured() {
    jwtProviderConfigured = true
}

/**
 * Clears the fail-fast flag; used by the framework test-suite between isolated applications.
 */
internal fun resetJwtProviderFlag() {
    jwtProviderConfigured = false
}

/**
 * Reads the `roles` claim of a verified principal.
 *
 * @param principal verified token principal.
 * @return the role set carried by the token.
 */
internal fun jwtRolesOf(principal: JWTPrincipal): Set<String> =
    principal.payload.getClaim(JwtConfig.CLAIM_ROLES).asList(String::class.java)?.toSet() ?: emptySet()

/**
 * Raised when a required `@JwtUser`/`@JwtClaims` parameter cannot be resolved from the request.
 *
 * Translated into a sanitized HTTP 401; the controller body never runs for an unauthenticated
 * required-parameter route.
 */
class JwtPrincipalMissingException : RuntimeException("JWT principal is missing for a required parameter")
