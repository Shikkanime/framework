package fr.shikkanime.ktor

import fr.shikkanime.ktor.auth.JwtAuthenticated
import fr.shikkanime.ktor.auth.JWT_AUTH_PROVIDER_NAME
import fr.shikkanime.ktor.auth.JwtRoles
import fr.shikkanime.ktor.auth.jwtRolesOf
import fr.shikkanime.ktor.auth.requireJwtProvider
import fr.shikkanime.ktor.dtos.MessageDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

/**
 * Discovers annotated controller methods and registers them in a Ktor routing tree.
 *
 * Only instances annotated with [RestController] are considered. Methods annotated with
 * [GetMapping], [PostMapping], or [PatchMapping] are bound below the controller path and delegated
 * to [ControllerRequestHandler]. OpenAPI metadata is generated from the endpoint annotations.
 * Methods annotated [JwtAuthenticated] or [JwtRoles] are additionally wrapped in the `jwt-auth`
 * authentication provider configured by `fr.shikkanime.ktor.auth.configureJwtAuth`.
 */
object ControllerBinder {
    /**
     * Registers every supported endpoint declared by [instances].
     *
     * Objects without [RestController] are ignored, as are methods without a supported mapping
     * annotation. If a method has several mapping annotations, their precedence is GET, POST, then
     * PATCH.
     *
     * @param routing Ktor routing tree receiving the generated routes.
     * @param instances initialized controller instances whose methods should be bound.
     * @throws IllegalStateException when a method requires JWT authentication but
     * `configureJwtAuth` was never called on the application.
     */
    fun register(routing: Routing, instances: List<Any>) {
        instances.forEach { instance ->
            val kClass = instance::class
            val restController = kClass.findAnnotation<RestController>() ?: return@forEach

            @OptIn(ExperimentalKtorApi::class)
            routing.route(restController.path) {
                kClass.functions.forEach { kFunction ->
                    requireJwtProvider(kFunction)

                    val requestHandler = ControllerRequestHandler.create(kClass, kFunction, instance)
                    val jwtAnnotation = kFunction.findAnnotation<JwtAuthenticated>()
                    val jwtRoles = kFunction.findAnnotation<JwtRoles>()

                    if (jwtRoles != null && jwtAnnotation?.optional == true)
                        throw IllegalStateException(
                            "@JwtRoles on ${kFunction.name} cannot be combined with @JwtAuthenticated(optional = true): " +
                                    "role enforcement requires authentication"
                        )

                    val optional = jwtAnnotation?.optional ?: false

                    suspend fun RoutingContext.enforceRolesAndInvoke() {
                        val principal = call.principal<JWTPrincipal>()

                        if (principal != null && jwtRolesOf(principal).none { role -> jwtRoles!!.roles.contains(role) })
                            call.respond(HttpStatusCode.Forbidden, MessageDto.error("You are not authorized to access this resource"))
                        else
                            requestHandler()
                    }

                    val guardedHandler: suspend RoutingContext.() -> Unit = when {
                        jwtRoles != null -> {
                            val guarded: suspend RoutingContext.() -> Unit = { enforceRolesAndInvoke() }
                            guarded
                        }

                        else -> requestHandler
                    }

                    val requiresAuth = jwtAnnotation != null || jwtRoles != null
                    val path = when {
                        kFunction.hasAnnotation<GetMapping>() -> kFunction.findAnnotation<GetMapping>()!!.path
                        kFunction.hasAnnotation<PostMapping>() -> kFunction.findAnnotation<PostMapping>()!!.path
                        kFunction.hasAnnotation<PatchMapping>() -> kFunction.findAnnotation<PatchMapping>()!!.path
                        else -> return@forEach
                    }
                    val verb = when {
                        kFunction.hasAnnotation<GetMapping>() -> "get"
                        kFunction.hasAnnotation<PostMapping>() -> "post"
                        else -> "patch"
                    }

                    if (requiresAuth) {
                        authenticate(JWT_AUTH_PROVIDER_NAME, optional = optional) {
                            when (verb) {
                                "get" -> get(path, guardedHandler)
                                "post" -> post(path, guardedHandler)
                                else -> patch(path, guardedHandler)
                            }.describe { describeOperation(kFunction) }
                        }
                    } else {
                        when (verb) {
                            "get" -> get(path, requestHandler)
                            "post" -> post(path, requestHandler)
                            else -> patch(path, requestHandler)
                        }.describe { describeOperation(kFunction) }
                    }
                }
            }
        }
    }
}
