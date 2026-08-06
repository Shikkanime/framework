package fr.shikkanime

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class SingleFlight {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

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