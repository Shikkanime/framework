package fr.shikkanime.cache

import kotlin.time.Duration

/**
 * High-level asynchronous key-value cache client wrapper contract for Valkey / Redis interactions.
 */
interface ValkeyWrapper {
    /**
     * Retrieves the binary payload stored at [key], or `null` if the key does not exist.
     *
     * @param key Entry key identifier.
     * @return The raw byte array payload, or `null` if missing.
     */
    suspend fun get(key: String): ByteArray?

    /**
     * Stores a binary payload at [key] with a given expiration [ttl].
     *
     * @param key Entry key identifier.
     * @param value The raw byte array payload.
     * @param ttl Expiration duration for the key.
     */
    suspend fun set(key: String, value: ByteArray, ttl: Duration)

    /**
     * Atomically increments the integer value stored at [key] by one.
     *
     * @param key Entry key identifier.
     * @return The updated long value after incrementing.
     */
    suspend fun incr(key: String): Long
}