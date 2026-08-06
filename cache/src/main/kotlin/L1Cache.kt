package fr.shikkanime

import fr.shikkanime.L1Cache.Entry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class L1Cache(private val maxSize: Int = 10_000, private val defaultTtl: Duration = 30.seconds) {
    private data class Entry(val value: Any?, val expiresAtNano: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String?, Entry?>?): Boolean =
            size > maxSize
    }

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

    @Synchronized
    fun put(key: String, value: Any?, ttl: Duration) {
        entries[key] = Entry(value, System.nanoTime() + ttl.inWholeNanoseconds)
    }

    @Synchronized
    fun put(key: String, value: Any?) =
        put(key, value, defaultTtl)

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}