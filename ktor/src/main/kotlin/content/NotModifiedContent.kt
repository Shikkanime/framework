package fr.shikkanime.ktor.content

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent

/**
 * Empty body of a `304` answer, echoing the validator the client revalidated with.
 *
 * A client holding the payload sends its entity tag back: answering `304` costs a header round trip
 * instead of the payload.
 *
 * @property etag validator of the payload the client already holds, when the caller publishes one.
 * @see fr.shikkanime.ktor.ResponseEntity.notModified
 */
class NotModifiedContent(
    private val etag: String? = null
) : OutgoingContent.NoContent() {
    override val headers: Headers = Headers.build {
        etag?.let { append(HttpHeaders.ETag, "\"$it\"") }
    }
}
