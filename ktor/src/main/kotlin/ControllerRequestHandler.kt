@file:Suppress("DEPRECATION") // `typeInfoImpl` is Ktor's public factory while its constructor is internal.

package fr.shikkanime.ktor

import fr.shikkanime.core.LoggerFactory
import fr.shikkanime.ktor.dtos.MessageDto
import fr.shikkanime.validator.Validator
import fr.shikkanime.validator.exceptions.ObjectNotValidException
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfoImpl
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.hasAnnotation

/**
 * Creates Ktor handlers that invoke controller functions and translate their results to responses.
 */
internal object ControllerRequestHandler {
    private val logger = LoggerFactory.getLogger()

    private const val MILLIS_IN_NANOSECONDS = 1_000_000.0
    private const val KIBIBYTE = 1_024.0
    private const val KIBIBYTE_FORMAT = "%.2f KiB"
    private const val MEBIBYTE = 1_048_576.0
    private const val MEBIBYTE_FORMAT = "%.2f MiB"

    /**
     * Creates a request handler for one reflected controller function.
     *
     * Arguments are resolved from the request, values annotated with [Valid] are validated, and
     * [ResponseEntity] results control the response status. Validation failures produce HTTP 400;
     * all other failures produce HTTP 500. Request duration and response size are logged.
     *
     * @param controllerClass runtime class used in request logs.
     * @param function controller function to invoke.
     * @param instance controller instance receiving the invocation.
     * @return a Ktor routing handler for the function.
     */
    fun create(
        controllerClass: KClass<*>,
        function: KFunction<*>,
        instance: Any
    ): suspend RoutingContext.() -> Unit = {
        logger.info("Start - ${controllerClass.simpleName}.${function.name}")
        val start = System.nanoTime()
        try {
            // Argument resolution belongs to the guarded part: a malformed parameter is a client
            // error and must reach the refusal below, not escape to a 500.
            val args = ControllerArgumentResolver.resolve(call, function, instance)

            args.filter { (kParameter, value) -> kParameter.hasAnnotation<Valid>() && value != null }
                .forEach { (_, value) -> Validator.validate(value!!) }

            val typeInfo = responseTypeInfo(function)

            when (val result = function.callSuspendBy(args)) {
                is ResponseEntity<*> ->
                    if (typeInfo == null) {
                        call.respond(result.status, result.body ?: "")
                    } else {
                        call.respond(result.status, result.body ?: "", typeInfo)
                    }

                else ->
                    if (typeInfo == null) {
                        call.respond(result ?: "")
                    } else {
                        call.respond(result ?: "", typeInfo)
                    }
            }
        } catch (exception: Exception) {
            val cause = (exception as? InvocationTargetException)?.targetException ?: exception
            cause.printStackTrace()

            when (cause) {
                is ObjectNotValidException ->
                    call.respond(HttpStatusCode.BadRequest, MessageDto.error(cause.message))

                is InvalidParameterException ->
                    call.respond(HttpStatusCode.BadRequest, MessageDto.error(cause.message))

                else ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        MessageDto.error("An unexpected error occurred")
                    )
            }
        }

        val durationMs = (System.nanoTime() - start) / MILLIS_IN_NANOSECONDS
        val sizeLog = call.attributes.getOrNull(ResponseSizeAttribute)?.let(::formatBytes) ?: "NaN"

        logger.info(
            "End - ${controllerClass.simpleName}.${function.name} " +
                "(${"%.2f ms".format(durationMs)}; $sizeLog)"
        )
    }

    /**
     * Resolves the serialization type of the body a controller function declares.
     *
     * Ktor serializes a response from the type the call site knows, and [ControllerRequestHandler]
     * invokes every function through a star-projected return type. A generic body - a `PageDto<T>`,
     * a `List<T>` - would therefore lose its type arguments and fail to find a serializer. The
     * declared return type carries them; a `ResponseEntity<T>` exposes the body as its first type
     * argument, and a plain return type is the body itself.
     *
     * `Any` deliberately answers `null`: a route that declares no precise type keeps Ktor's runtime
     * lookup, which is how an error payload of another type reaches the same declaration.
     *
     * @param function controller function whose declared return type is read.
     * @return the body type, or `null` when the declaration does not describe one precisely.
     */
    private fun responseTypeInfo(function: KFunction<*>): TypeInfo? {
        val declared = function.returnType
        val bodyType = if (declared.classifier == ResponseEntity::class) {
            declared.arguments.firstOrNull()?.type
        } else {
            declared
        } ?: return null

        val type = bodyType.classifier as? KClass<*> ?: return null

        @Suppress("DEPRECATION") // The constructor is internal, and this factory is Ktor's public one.
        return if (type == Any::class) null else typeInfoImpl(type.java, type, bodyType)
    }

    /**
     * Formats a byte count using bytes, kibibytes, or mebibytes.
     *
     * @param bytes non-negative response size in bytes.
     * @return human-readable binary size.
     */
    private fun formatBytes(bytes: Long): String =
        when {
            bytes < KIBIBYTE -> "$bytes B"
            bytes < MEBIBYTE -> KIBIBYTE_FORMAT.format(bytes / KIBIBYTE)
            else -> MEBIBYTE_FORMAT.format(bytes / MEBIBYTE)
        }
}
