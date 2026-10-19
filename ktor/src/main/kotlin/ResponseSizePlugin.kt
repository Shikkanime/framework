package fr.shikkanime.ktor

import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.util.AttributeKey

/**
 * Call attribute carrying the size of the response payload, in bytes.
 *
 * Populated by [ResponseSizeRecorder] once the outgoing content is ready to be sent, and read by
 * the request logs to report a response size.
 */
val ResponseSizeAttribute: AttributeKey<Long> = AttributeKey("ResponseSize")

/**
 * Records the size of every response payload into [ResponseSizeAttribute].
 *
 * The Content-Length header is computed by the engine while writing and is often never exposed on
 * the response headers, which made every request log print `NaN`. Measuring the outgoing content
 * at send time is the only source that always exists, whatever the engine and the negotiated
 * format.
 *
 * @see io.ktor.server.response.ApplicationSendPipeline
 */
val ResponseSizeRecorder: ApplicationPlugin<Unit> =
    createApplicationPlugin(name = "ResponseSizeRecorder") {
        on(ResponseBodyReadyForSend) { _, call, content ->
            call.attributes.put(
                ResponseSizeAttribute,
                when (content) {
                    is OutgoingContent.ByteArrayContent -> content.bytes().size.toLong()
                    is OutgoingContent.NoContent -> 0L
                    else -> content.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                }
            )
        }
    }
