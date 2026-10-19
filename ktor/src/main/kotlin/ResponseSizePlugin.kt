package fr.shikkanime.ktor

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.io.InputStream
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
        on(ResponseBodyReadyForSend) { context, call, content ->
            val bytes: ByteArray? = when (content) {
                is OutgoingContent.ByteArrayContent -> content.bytes()
                is OutgoingContent.NoContent -> ByteArray(0)
                else -> null
            }

            if (bytes != null) {
                call.attributes.put(ResponseSizeAttribute, bytes.size.toLong())
            } else {
                // A streamed payload carries no size anywhere: materialize it once, measure it,
                // and send the very same bytes.
                val materialized = (content as OutgoingContent.ReadChannelContent)
                    .readFrom()
                    .readRemaining()
                    .readByteArray()

                call.attributes.put(ResponseSizeAttribute, materialized.size.toLong())
                context.transformBodyTo(
                    object : OutgoingContent.ByteArrayContent() {
                        override val contentType: ContentType? = content.contentType
                        override val headers: Headers = content.headers
                        override fun bytes(): ByteArray = materialized
                    }
                )
            }
        }
    }
