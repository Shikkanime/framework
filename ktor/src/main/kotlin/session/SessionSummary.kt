package fr.shikkanime.ktor.session

import kotlinx.serialization.Serializable

/**
 * Lightweight description of one session, used by the list API.
 *
 * @property sessionId identifier of the session.
 * @property service service that produced the events.
 * @property firstSeen timestamp of the oldest event, when the journal has a readable event.
 * @property lastSeen timestamp of the newest event, when the journal has a readable event.
 * @property sizeBytes size of the journal file in bytes.
 */
@Serializable
data class SessionSummary(
    val sessionId: String,
    val service: String,
    val firstSeen: String?,
    val lastSeen: String?,
    val sizeBytes: Long
)
