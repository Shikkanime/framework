package fr.shikkanime

import kotlin.time.Duration

interface ValkeyWrapper {
    suspend fun get(key: String): ByteArray?

    suspend fun set(key: String, value: ByteArray, ttl: Duration)

    suspend fun incr(key: String): Long
}