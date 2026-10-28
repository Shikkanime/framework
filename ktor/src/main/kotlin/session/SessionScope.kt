package fr.shikkanime.ktor.session

/**
 * Correlation identifiers of the request currently executing.
 *
 * @property sessionId long-lived session identifier.
 * @property requestId identifier of the current request.
 * @property parentRequestId identifier of the request that triggered this one, when any.
 * @property service name of the service executing the request.
 * @property store journal receiving the events, when tracking is enabled.
 */
data class SessionScope(
    val sessionId: String,
    val requestId: String,
    val parentRequestId: String? = null,
    val service: String,
    val store: SessionStore? = null
)
