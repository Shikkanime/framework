package fr.shikkanime.ktor.session

import fr.shikkanime.ktor.ErrorAttribute
import fr.shikkanime.ktor.ResponseSizeAttribute
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.request.*
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Installs the session tracking plugin on this application.
 *
 * @param config session tracking configuration.
 * @param store journal receiving the events.
 * @param closeStoreOnStop whether the store is owned by the framework and must be closed when the
 * application stops.
 */
internal fun Application.installSessionTracking(
    config: SessionConfig,
    store: SessionStore,
    closeStoreOnStop: Boolean
) {
    install(sessionTrackingPlugin(config, store, closeStoreOnStop))
}

private fun sessionTrackingPlugin(
    config: SessionConfig,
    store: SessionStore,
    closeStoreOnStop: Boolean
): ApplicationPlugin<Unit> =
    createApplicationPlugin(name = "SessionTracking") {
        val service = config.resolvedService()
        application.intercept(ApplicationCallPipeline.Setup) {
            if (isExcluded(call.request, config)) {
                proceed()
                return@intercept
            }
            val scope = resolveScope(call, config, store, service)
            if (scope == null) {
                proceed()
                return@intercept
            }
            call.attributes.put(SessionScopeAttribute, scope)
            call.attributes.put(SessionStartAttribute, System.nanoTime())
            call.response.headers.append(SessionHeaders.SESSION_ID, scope.sessionId)
            call.response.headers.append(SessionHeaders.REQUEST_ID, scope.requestId)
            withContext(SessionContextElement(scope)) { proceed() }
        }
        on(ResponseBodyReadyForSend) { _, call, _ ->
            recordInbound(call, store)
        }
        if (closeStoreOnStop) {
            application.monitor.subscribe(ApplicationStopped) { store.close() }
        }
    }

private fun isExcluded(request: ApplicationRequest, config: SessionConfig): Boolean {
    if (request.httpMethod == HttpMethod.Options) return true
    val path = request.path()
    return config.excludedPathPrefixes.any { path.startsWith(it) }
}

private fun resolveScope(
    call: ApplicationCall,
    config: SessionConfig,
    store: SessionStore,
    service: String
): SessionScope? {
    val requestedSession = call.request.headers[SessionHeaders.SESSION_ID]
    if (!config.generateIfMissing && !SessionIds.isValid(requestedSession)) return null
    return SessionScope(
        sessionId = SessionIds.resolveSession(requestedSession),
        requestId = SessionIds.resolveRequest(call.request.headers[SessionHeaders.REQUEST_ID]),
        parentRequestId = call.request.headers[SessionHeaders.PARENT_REQUEST_ID]?.takeIf(SessionIds::isValid),
        service = service,
        store = store
    )
}

private fun recordInbound(call: ApplicationCall, store: SessionStore) {
    val scope = call.attributes.getOrNull(SessionScopeAttribute) ?: return
    val startedAt = call.attributes.getOrNull(SessionStartAttribute) ?: return
    store.append(
        SessionEvent(
            timestamp = Instant.now().toString(),
            sessionId = scope.sessionId,
            requestId = scope.requestId,
            parentRequestId = scope.parentRequestId,
            service = scope.service,
            kind = SessionEventKind.IN,
            method = call.request.httpMethod.value,
            path = call.request.path(),
            status = call.response.status()?.value,
            durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI,
            size = call.attributes.getOrNull(ResponseSizeAttribute),
            error = call.attributes.getOrNull(ErrorAttribute)?.toSessionError()
        )
    )
}
