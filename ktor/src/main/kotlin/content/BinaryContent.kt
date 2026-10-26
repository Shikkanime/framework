package fr.shikkanime.ktor.content

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent

/**
 * Binary response body, with the metadata a cache needs.
 *
 * The binder answers with this content as-is, so its content type and its headers reach the client:
 * an artwork, a generated document or a CSV export is served without escaping the controller
 * pipeline.
 *
 * @property bytes raw payload.
 * @property contentType media type of the payload.
 * @property etag validator identifying this exact payload, when the caller publishes one.
 * @property cacheControl `Cache-Control` directive of the response, when the caller publishes one.
 * @see fr.shikkanime.ktor.ResponseEntity.binary
 */
class BinaryContent(
    private val bytes: ByteArray,
    override val contentType: ContentType,
    etag: String? = null,
    cacheControl: String? = null
) : OutgoingContent.ByteArrayContent() {
    override val contentLength: Long = bytes.size.toLong()

    override val headers: Headers = Headers.build {
        etag?.let { append(HttpHeaders.ETag, "\"$it\"") }
        cacheControl?.let { append(HttpHeaders.CacheControl, it) }
    }

    override fun bytes(): ByteArray = bytes
}
