package fr.shikkanime.ktor

import fr.shikkanime.ktor.content.BinaryContent
import fr.shikkanime.ktor.content.LocationContent
import fr.shikkanime.ktor.content.NotModifiedContent
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent

/**
 * Couples a response body with the HTTP status returned by [ControllerBinder].
 *
 * @param T response body type.
 * @property body value serialized by Ktor.
 * @property status HTTP response status.
 */
data class ResponseEntity<T>(
    val body: T,
    val status: HttpStatusCode = HttpStatusCode.OK
) {
    /**
     * Factory methods for common HTTP response statuses.
     */
    companion object {
        /** Creates an HTTP 200 response containing [body]. */
        fun <T> ok(body: T) = ResponseEntity(body, HttpStatusCode.OK)

        /** Creates an HTTP 201 response containing [body]. */
        fun <T> created(body: T) = ResponseEntity(body, HttpStatusCode.Created)

        /** Creates an HTTP 400 response containing [body]. */
        fun <T> badRequest(body: T) = ResponseEntity(body, HttpStatusCode.BadRequest)

        /** Creates an HTTP 404 response containing [body]. */
        fun <T> notFound(body: T) = ResponseEntity(body, HttpStatusCode.NotFound)

        /** Creates an HTTP 409 response containing [body]. */
        fun <T> conflict(body: T) = ResponseEntity(body, HttpStatusCode.Conflict)

        /** Creates an HTTP 500 response containing [body]. */
        fun <T> internalServerError(body: T) = ResponseEntity(body, HttpStatusCode.InternalServerError)

        /**
         * Creates an HTTP 200 response carrying raw [payload] bytes.
         *
         * The payload is served as-is, with its media type, and with the validators a cache needs:
         * this is how a controller returns an image, a generated document or an export without
         * leaving the controller pipeline.
         *
         * @param payload raw bytes to send.
         * @param contentType media type of the payload.
         * @param etag validator identifying this exact payload, when the caller publishes one.
         * @param cacheControl `Cache-Control` directive of the response, when the caller publishes one.
         * @return an HTTP 200 response holding the payload.
         * @see [BinaryContent]
         */
        fun binary(
            payload: ByteArray,
            contentType: ContentType,
            etag: String? = null,
            cacheControl: String? = null
        ): ResponseEntity<OutgoingContent> =
            ResponseEntity(BinaryContent(payload, contentType, etag, cacheControl), HttpStatusCode.OK)

        /**
         * Creates an HTTP 302 response pointing at [location].
         *
         * @param location address the client is sent to.
         * @return an HTTP 302 response carrying the location.
         * @see [LocationContent]
         */
        fun redirect(location: String): ResponseEntity<OutgoingContent> =
            ResponseEntity(LocationContent(location), HttpStatusCode.Found)

        /**
         * Creates an HTTP 304 response, echoing the validator the client revalidated with.
         *
         * @param etag validator of the payload the client already holds, when the caller publishes one.
         * @return an HTTP 304 response carrying the validator.
         * @see [NotModifiedContent]
         */
        fun notModified(etag: String? = null): ResponseEntity<OutgoingContent> =
            ResponseEntity(NotModifiedContent(etag), HttpStatusCode.NotModified)
    }
}
