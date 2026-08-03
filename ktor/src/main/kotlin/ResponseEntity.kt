package fr.shikkanime.ktor

import io.ktor.http.*

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
    }
}
