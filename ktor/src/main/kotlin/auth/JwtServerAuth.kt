package fr.shikkanime.ktor.auth

import fr.shikkanime.ktor.dtos.MessageDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

/**
 * Name of the Ktor authentication provider installed by [configureJwtAuth].
 */
const val JWT_AUTH_PROVIDER_NAME = "jwt-auth"

/**
 * Installs the `jwt-auth` Ktor authentication provider backed by [tokenService].
 *
 * The provider delegates signature, algorithm, expiry, and `token_type=access` enforcement to the
 * pinned verifier of [JwtTokenService], then checks the audience. Challenges answer a sanitized
 * HTTP 401 [MessageDto] identical for every failure cause, so no oracle distinguishes expired
 * from tampered tokens.
 *
 * Call this before [fr.shikkanime.ktor.ControllerBinder.register] so annotated routes can bind
 * against the provider.
 *
 * @param application Ktor application receiving the authentication plugin.
 * @param tokenService JWT machinery created at startup; its config supplies realm and audience.
 */
fun Application.configureJwtAuth(tokenService: JwtTokenService) {
    markJwtProviderConfigured()

    install(Authentication) {
        jwt(JWT_AUTH_PROVIDER_NAME) {
            realm = tokenService.config.realm
            verifier(tokenService.accessVerifier)
            validate { credential ->
                if (tokenService.config.audience in credential.payload.audience)
                    JWTPrincipal(credential.payload)
                else
                    null
            }
            challenge { _, _ ->
                call.response.headers.append(
                    HttpHeaders.WWWAuthenticate,
                    """Bearer realm="${tokenService.config.realm}""""
                )
                call.respond(
                    HttpStatusCode.Unauthorized,
                    MessageDto.error("You are not authorized to access this resource")
                )
            }
        }
    }
}
