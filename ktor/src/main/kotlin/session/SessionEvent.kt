package fr.shikkanime.ktor.session

import kotlinx.serialization.Serializable

/**
 * One entry of a session journal, serialized as a single JSON line.
 *
 * @property timestamp event time, in ISO-8601 UTC.
 * @property sessionId session this event belongs to.
 * @property requestId identifier of the request that produced the event.
 * @property parentRequestId identifier of the request that triggered an outgoing call, when any.
 * @property service name of the service that wrote the event.
 * @property kind nature of the event.
 * @property method HTTP method, for request events.
 * @property path request path without its query string.
 * @property target host of an outgoing call.
 * @property status HTTP status, when the event has one.
 * @property durationMs handler duration in milliseconds.
 * @property size response payload size in bytes, when known.
 * @property error exception type and message, bounded by the store.
 * @property name application-defined event name.
 * @property data application-defined event attributes.
 */
@Serializable
data class SessionEvent(
    val timestamp: String,
    val sessionId: String,
    val requestId: String,
    val parentRequestId: String? = null,
    val service: String,
    val kind: SessionEventKind,
    val method: String? = null,
    val path: String? = null,
    val target: String? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val size: Long? = null,
    val error: String? = null,
    val name: String? = null,
    val data: Map<String, String> = emptyMap()
)
