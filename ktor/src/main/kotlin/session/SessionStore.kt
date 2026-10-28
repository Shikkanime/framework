package fr.shikkanime.ktor.session

/**
 * Persistent store of session events.
 *
 * Implementations must make [append] cheap and non-blocking: it is called from the request thread.
 * The framework ships [FileSessionStore]; a project that needs a database implements this interface
 * and passes the instance through [SessionConfig.store].
 */
interface SessionStore {
    /**
     * Appends one event.
     *
     * @param event event to persist.
     */
    fun append(event: SessionEvent)

    /**
     * Reads the most recent events of one session.
     *
     * @param sessionId session to read.
     * @param limit maximum number of events returned, from the end of the journal.
     * @return the session timeline, or `null` when the session has no readable event.
     */
    suspend fun find(sessionId: String, limit: Int = DEFAULT_EVENT_LIMIT): SessionTrace?

    /**
     * Lists the most recently active sessions.
     *
     * @param limit maximum number of sessions returned.
     * @return session summaries, most recent first.
     */
    suspend fun sessions(limit: Int = DEFAULT_SESSION_LIMIT): List<SessionSummary>

    /** Flushes the events queued by [append]. */
    fun flush()

    /** Flushes and releases the resources of the store. */
    fun close()

    /** Defaults used by the read API. */
    companion object {
        /** Default number of events returned by [find]. */
        const val DEFAULT_EVENT_LIMIT = 500

        /** Default number of sessions returned by [sessions]. */
        const val DEFAULT_SESSION_LIMIT = 50
    }
}
