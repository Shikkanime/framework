package fr.shikkanime.ktor.session

import java.time.Instant

/**
 * Holds the [SessionScope] of the request running on the current thread.
 *
 * Ktor installs the scope for the whole call pipeline; code running inside a controller, a service
 * or a use case can read it, and the framework's HTTP client forwards it to outgoing calls. A
 * coroutine keeps the scope across thread switches through [SessionContextElement].
 */
object SessionContext {
    private val holder = ThreadLocal<SessionScope?>()

    /**
     * Returns the scope of the current request, or `null` outside a tracked request.
     *
     * @return the current scope, if any.
     */
    fun current(): SessionScope? =
        holder.get()

    /**
     * Installs a scope on the current thread and returns the previous one.
     *
     * @param scope scope to install.
     * @return the scope that was installed before, or `null`.
     */
    internal fun install(scope: SessionScope): SessionScope? {
        val previous = holder.get()
        holder.set(scope)
        return previous
    }

    /**
     * Restores the scope installed before [install].
     *
     * @param previous scope returned by [install].
     */
    internal fun restore(previous: SessionScope?) {
        if (previous == null) holder.remove() else holder.set(previous)
    }

    /**
     * Runs [block] with [scope] installed, restoring the previous scope afterwards.
     *
     * This is the entry point for a background job that calls other services and wants its calls
     * correlated under one session.
     *
     * @param scope scope to install for the duration of [block].
     * @param block code to run.
     * @param T result type of [block].
     * @return the result of [block].
     */
    fun <T> with(scope: SessionScope, block: () -> T): T {
        val previous = install(scope)
        return try {
            block()
        } finally {
            restore(previous)
        }
    }

    /**
     * Appends an application-defined event to the journal of the current session.
     *
     * The call is a no-op outside a tracked request or when no store is configured.
     *
     * @param name event name, for example `cache.miss`.
     * @param data event attributes.
     */
    fun event(name: String, data: Map<String, String> = emptyMap()) {
        val scope = current() ?: return
        scope.store?.append(
            SessionEvent(
                timestamp = Instant.now().toString(),
                sessionId = scope.sessionId,
                requestId = scope.requestId,
                parentRequestId = scope.parentRequestId,
                service = scope.service,
                kind = SessionEventKind.EVENT,
                name = name,
                data = data
            )
        )
    }
}
