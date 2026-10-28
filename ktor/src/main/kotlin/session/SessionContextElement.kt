package fr.shikkanime.ktor.session

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Carries the [SessionScope] across coroutine thread switches.
 *
 * The element sets the [SessionContext] thread-local before a coroutine resumes on a thread and
 * restores the previous value after it suspends, which keeps the scope visible to code that runs
 * on a different dispatcher.
 */
internal class SessionContextElement(
    private val scope: SessionScope
) : ThreadContextElement<SessionScope?>, AbstractCoroutineContextElement(Key) {
    /** Coroutine context key of the element. */
    companion object Key : CoroutineContext.Key<SessionContextElement>

    override fun updateThreadContext(context: CoroutineContext): SessionScope? =
        SessionContext.install(scope)

    override fun restoreThreadContext(context: CoroutineContext, oldState: SessionScope?) =
        SessionContext.restore(oldState)
}
