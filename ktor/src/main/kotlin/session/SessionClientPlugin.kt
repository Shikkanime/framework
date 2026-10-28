package fr.shikkanime.ktor.session

import io.ktor.client.*
import io.ktor.client.plugins.*
import java.time.Instant

/**
 * Forwards the current session to every outgoing HTTP call.
 *
 * The client replaces any session headers set by the caller with the identifiers of the current
 * request, so a service cannot accidentally detach its outgoing calls from the session it serves.
 * The call is recorded as an `OUT` event when a store is available.
 */
internal fun HttpClient.installSessionPropagation() {
    plugin(HttpSend).intercept { request ->
        val scope = SessionContext.current() ?: return@intercept execute(request)
        val childRequestId = SessionIds.newRequestId()
        request.headers[SessionHeaders.SESSION_ID] = scope.sessionId
        request.headers[SessionHeaders.REQUEST_ID] = childRequestId
        request.headers[SessionHeaders.PARENT_REQUEST_ID] = scope.requestId
        val method = request.method.value
        val path = request.url.encodedPathSegments
            .filter { it.isNotEmpty() }
            .joinToString("/", prefix = "/")
        val target = request.url.host
        val startedAt = System.nanoTime()
        try {
            val call = execute(request)
            recordOutbound(scope, childRequestId, method, path, target, call.response.status.value, startedAt)
            call
        } catch (exception: Exception) {
            recordOutbound(scope, childRequestId, method, path, target, null, startedAt, exception)
            throw exception
        }
    }
}

private fun recordOutbound(
    scope: SessionScope,
    requestId: String,
    method: String,
    path: String,
    target: String,
    status: Int?,
    startedAt: Long,
    error: Throwable? = null
) {
    scope.store?.append(
        SessionEvent(
            timestamp = Instant.now().toString(),
            sessionId = scope.sessionId,
            requestId = requestId,
            parentRequestId = scope.requestId,
            service = scope.service,
            kind = SessionEventKind.OUT,
            method = method,
            path = path,
            target = target,
            status = status,
            durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI,
            error = error?.toSessionError()
        )
    )
}
