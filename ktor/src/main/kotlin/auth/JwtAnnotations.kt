package fr.shikkanime.ktor.auth

/**
 * Gates a controller method behind JWT authentication.
 *
 * The framework's [ControllerBinder] wraps the route in the `jwt-auth` Ktor authentication
 * provider configured by [configureJwtAuth]. With [optional] set to `true`, unauthenticated
 * requests still reach the method but without a principal — see the `@JwtUser` and `@JwtClaims`
 * nullability contract.
 *
 * @property optional whether anonymous requests are allowed through.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JwtAuthenticated(
    val optional: Boolean = false
)

/**
 * Resolves the authenticated subject into the annotated controller parameter.
 *
 * The value comes from the `uuid` claim of the verified access token. Supported parameter types
 * are `kotlin.uuid.Uuid` and `String`. With [JwtAuthenticated.optional] and no token, the value
 * is bound only when the parameter is nullable — required parameters abort with HTTP 401.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class JwtUser

/**
 * Injects the verified token view into the annotated controller parameter.
 *
 * The parameter type must be [JwtPrincipalView]. Follows the same nullability contract as [JwtUser].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class JwtClaims

/**
 * Restricts a controller method to authenticated subjects carrying at least one of [roles].
 *
 * Authentication happens first (HTTP 401 without a valid token), then the role check responds
 * HTTP 403 when none of [roles] appears in the token's `roles` claim.
 *
 * @property roles role names granting access.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JwtRoles(
    vararg val roles: String
)
