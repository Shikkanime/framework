package fr.shikkanime.ktor.session

import kotlinx.serialization.Serializable

/**
 * Nature of one entry in a session journal.
 */
@Serializable
enum class SessionEventKind {
    /** A request received by this service. */
    IN,

    /** A request sent by this service to another service. */
    OUT,

    /** An application-defined event attached to the current session. */
    EVENT
}
