package fr.shikkanime.ktor.content

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent

/**
 * Empty body of a redirect, pointing the client at another address.
 *
 * @property location address the client is sent to.
 * @see fr.shikkanime.ktor.ResponseEntity.redirect
 */
class LocationContent(
    private val location: String
) : OutgoingContent.NoContent() {
    override val headers: Headers = Headers.build {
        append(HttpHeaders.Location, location)
    }
}
