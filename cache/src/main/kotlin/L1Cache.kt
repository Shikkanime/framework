package fr.shikkanime.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A thread-safe, in-memory Level 1 (L1) LRU (Least Recently Used) cache with entry TTL expiration.
 *
 * Enforces capacity restrictions based on [maxSize] using access-order [LinkedHashMap]
 * and evaluates item freshness against system nanoTime.
 *
 * @param maxSize Maximum number of items held concurrently in memory.
 * @param defaultTtl Default time-to-live duration for cached entries.
 */
class L1Cache(private val maxSize: Int = 10_000, private val defaultTtl: Duration = 30.seconds) {
    /**
     * Internal container holding the cached value along with its absolute nanosecond expiration timestamp.
     *
     * @property value The cached object instance (or `null`).
     * @property expiresAtNano System nanoTime after which this entry is considered stale.
     */
    private data class Entry(val value: Any?, val expiresAtNano: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String?, Entry?>?): Boolean =
            size > maxSize
    }

    /**
     * Retrieves an entry value by [key] if it exists and has not expired.
     * Automatically evicts the entry if its TTL has elapsed.
     *
     * @param key Unique entry identifier.
     * @return The cached value, or `null` if absent or expired.
     */
    @Synchronized
    fun get(key: String): Any? {
        val entry = entries[key] ?: return null
        return if (System.nanoTime() >= entry.expiresAtNano) {
            entries.remove(key)
            null
        } else {
            entry.value
        }
    }

    /**
     * Stores a key-value pair in L1 memory with a specific [ttl].
     *
     * @param key Unique entry identifier.
     * @param value Object instance to cache.
     * @param ttl Custom time-to-live duration.
     */
    @Synchronized
    fun put(key: String, value: Any?, ttl: Duration) {
        entries[key] = Entry(value, System.nanoTime() + ttl.inWholeNanoseconds)
    }

    /**
     * Stores a key-value pair in L1 memory using the default TTL.
     *
     * @param key Unique entry identifier.
     * @param value Object instance to cache.
     */
    @Synchronized
    fun put(key: String, value: Any?) =
        put(key, value, defaultTtl)

    /**
     * Evicts a single entry from L1 memory by its [key].
     *
     * @param key Unique entry identifier.
     */
    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    /**
     * Evicts all entries from L1 memory.
     */
    @Synchronized
    fun clear() {
        entries.clear()
    }
}