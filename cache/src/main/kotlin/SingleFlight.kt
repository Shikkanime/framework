package fr.shikkanime.cache

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * A concurrency control utility that suppresses duplicate in-flight async calls.
 *
 * Ensures that for a given [key], only one concurrent execution of [block] is performed at a time.
 * Subsequent concurrent callers for the same key await and receive the result (or exception)
 * produced by the first execution.
 */
class SingleFlight {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

    /**
     * Executes the given suspending [block] for a [key] if no matching invocation is currently active.
     * If an invocation is already in progress, awaits and returns its result.
     *
     * @param T The return type of the computation.
     * @param key Unique key identifying the operation to deduplicate.
     * @param block The asynchronous loader computation.
     * @return The result of the computation.
     */
    suspend fun <T> doOnce(key: String, block: suspend () -> T): T {
        inFlight[key]?.let { return it.await() as T }
        val deferred = CompletableDeferred<Any?>()
        val winner = inFlight.putIfAbsent(key, deferred)
        if (winner != null) return winner.await() as T

        return try {
            val result = block()
            deferred.complete(result)
            result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key)
        }
    }
}