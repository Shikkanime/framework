package fr.shikkanime.ktor

import fr.shikkanime.core.LoggerFactory
import fr.shikkanime.ktor.dtos.MessageDto
import fr.shikkanime.validator.Validator
import fr.shikkanime.validator.exceptions.ObjectNotValidException
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.hasAnnotation

/**
 * Creates Ktor handlers that invoke controller functions and translate their results to responses.
 */
internal object ControllerRequestHandler {
    private val logger = LoggerFactory.getLogger(ControllerBinder::class.java)

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
        val args = ControllerArgumentResolver.resolve(call, function, instance)

        try {
            args.filter { (kParameter, value) -> kParameter.hasAnnotation<Valid>() && value != null }
                .forEach { (_, value) -> Validator.validate(value!!) }

            when (val result = function.callSuspendBy(args)) {
                is ResponseEntity<*> ->
                    call.respond(result.status, result.body ?: "")

                else ->
                    call.respond(result ?: "")
            }
        } catch (exception: Exception) {
            val cause = (exception as? InvocationTargetException)?.targetException ?: exception
            cause.printStackTrace()

            when (cause) {
                is ObjectNotValidException ->
                    call.respond(HttpStatusCode.BadRequest, MessageDto.error(cause.message))

                else ->
                    call.respond(HttpStatusCode.InternalServerError, MessageDto.error("An unexpected error occurred"))
            }
        }

        val durationMs = (System.nanoTime() - start) / MILLIS_IN_NANOSECONDS
        val contentLength = call.response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val sizeLog = contentLength?.let(::formatBytes) ?: "NaN"

        logger.info("End - ${controllerClass.simpleName}.${function.name} (${"%.2f ms".format(durationMs)}; $sizeLog)")
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
