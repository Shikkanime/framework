package fr.shikkanime.ktor

import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

/**
 * Discovers annotated controller methods and registers them in a Ktor routing tree.
 *
 * Only instances annotated with [RestController] are considered. Methods annotated with
 * [GetMapping], [PostMapping], or [PatchMapping] are bound below the controller path and delegated
 * to [ControllerRequestHandler]. OpenAPI metadata is generated from the endpoint annotations.
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
     */
    fun register(routing: Routing, instances: List<Any>) {
        instances.forEach { instance ->
            val kClass = instance::class
            val restController = kClass.findAnnotation<RestController>() ?: return@forEach

            @OptIn(ExperimentalKtorApi::class)
            routing.route(restController.path) {
                kClass.functions.forEach { kFunction ->
                    val requestHandler = ControllerRequestHandler.create(kClass, kFunction, instance)

                    when {
                        kFunction.hasAnnotation<GetMapping>() ->
                            get(kFunction.findAnnotation<GetMapping>()!!.path, requestHandler)

                        kFunction.hasAnnotation<PostMapping>() ->
                            post(kFunction.findAnnotation<PostMapping>()!!.path, requestHandler)

                        kFunction.hasAnnotation<PatchMapping>() ->
                            patch(kFunction.findAnnotation<PatchMapping>()!!.path, requestHandler)

                        else -> null
                    }?.describe { describeOperation(kFunction) }
                }
            }
        }
    }
}
