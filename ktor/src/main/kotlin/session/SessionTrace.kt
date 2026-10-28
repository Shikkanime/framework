package fr.shikkanime.ktor.session

import kotlinx.serialization.Serializable

/**
 * Timeline of one session, as returned by the read API.
 *
 * @property sessionId identifier of the session.
 * @property service service that produced the events.
 * @property firstSeen timestamp of the oldest event.
 * @property lastSeen timestamp of the newest event.
 * @property eventCount number of events in the journal, including the ones outside [events].
 * @property truncated whether older events were omitted because of the requested limit.
 * @property events most recent events, in chronological order.
 */
@Serializable
data class SessionTrace(
    val sessionId: String,
    val service: String,
    val firstSeen: String,
    val lastSeen: String,
    val eventCount: Int,
    val truncated: Boolean,
    val events: List<SessionEvent>
)
