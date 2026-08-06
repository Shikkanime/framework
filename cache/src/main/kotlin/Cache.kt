package fr.shikkanime.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * High-performance, two-level caching facade for Shikkanime Framework.
 *
 * Integrates an in-memory L1 LRU cache ([L1Cache]) with a distributed L2 Valkey/Redis binary cache ([ValkeyWrapper]),
 * powered by CBOR binary encoding ([BinaryCodec]), bucket versioning for instant cache group invalidation,
 * and concurrent loader deduplication ([SingleFlight]).
 *
 * @param l1 In-memory Level 1 cache instance.
 * @param valkeyWrapper Distributed Level 2 Valkey client wrapper.
 * @param codec Binary serializer/deserializer.
 * @param singleFlight Concurrency deduplication utility.
 * @param versionCacheTtl Time-to-live duration for bucket version numbers in L1 memory.
 */
class Cache(
    private val l1: L1Cache = L1Cache(),
    private val valkeyWrapper: ValkeyWrapper,
    private val codec: BinaryCodec = BinaryCodec(),
    private val singleFlight: SingleFlight = SingleFlight(),
    private val versionCacheTtl: Duration = 30.seconds
) {
    /**
     * Generates the key used to look up or store the current version of a [bucket].
     *
     * @param bucket The bucket name.
     * @return The formatted version key string (e.g. `"ver:episodes"`).
     */
    private fun versionKey(bucket: String): String =
        "ver:$bucket"

    /**
     * Constructs a full composite key incorporating the bucket, current version, and item key.
     *
     * @param bucket The bucket name.
     * @param version The active bucket version.
     * @param key The specific item key.
     * @return The formatted composite key string (e.g. `"episodes:0:123"`).
     */
    private fun fullKey(bucket: String, version: Long, key: String): String =
        "$bucket:$version:$key"

    /**
     * Reads the current bucket version number, checking L1 memory first, then Valkey L2.
     * Caches the resolved version in L1 with [versionCacheTtl].
     *
     * @param bucket The bucket name.
     * @return The numeric version of the bucket (defaults to `0` if not set).
     */
    private suspend fun readVersion(bucket: String): Long {
        val cacheKey = versionKey(bucket)
        l1.get(cacheKey)?.let { return it as Long }
        val version = valkeyWrapper.get(cacheKey)
            ?.let { codec.decodeVersion(it) }
            ?: 0
        l1.put(cacheKey, version, versionCacheTtl)
        return version
    }

    /**
     * Internal lookup routine that checks L1 memory, then Valkey L2, and executes [loader] on miss,
     * saving the result in both L1 and Valkey L2.
     *
     * @param T The payload type.
     * @param fullKey Composite versioned key.
     * @param ttl Expiration duration for stored entries.
     * @param loader Fallback suspending supplier.
     * @param serializer Serialization strategy for [T].
     * @return The retrieved or freshly loaded item.
     */
    private suspend fun <T> loadAndStore(
        fullKey: String,
        ttl: Duration,
        loader: suspend () -> T,
        serializer: KSerializer<T>
    ): T {
        l1.get(fullKey)?.let { return it as T }

        valkeyWrapper.get(fullKey)?.let {
            val value = codec.decode(it, serializer)
            l1.put(fullKey, value, ttl)
            return value
        }

        val value = loader()
        l1.put(fullKey, value, ttl)
        valkeyWrapper.set(fullKey, codec.encode(value, serializer), ttl)
        return value
    }

    /**
     * Internal implementation of bucketed item lookup with explicit [KSerializer].
     *
     * Resolves the current bucket version, checks L1 cache, then L2 Valkey cache, and finally executes [loader]
     * using single-flight deduplication on cache misses.
     *
     * @param T The type of item being retrieved.
     * @param bucket Logical grouping bucket name.
     * @param key Item key within the bucket.
     * @param ttl Expiration duration for the item.
     * @param loader Fallback suspending supplier invoked when the item is absent from L1 and L2 cache.
     * @param serializer Kotlinx serializer for [T].
     * @return The cached or newly loaded item.
     */
    suspend fun <T> getInternal(
        bucket: String,
        key: String,
        ttl: Duration,
        loader: suspend () -> T,
        serializer: KSerializer<T>
    ): T {
        val fullKey = fullKey(bucket, readVersion(bucket), key)
        return singleFlight.doOnce(fullKey) {
            loadAndStore(fullKey, ttl, loader, serializer)
        }
    }

    /**
     * Retrieves an item from the cache, executing [loader] on cache miss and inferring serializer via reified type.
     *
     * @param T The serializable type of item being retrieved.
     * @param bucket Logical grouping bucket name.
     * @param key Item key within the bucket.
     * @param ttl Expiration duration for the item in L1 and L2 cache.
     * @param loader Fallback suspending supplier invoked on cache miss.
     * @return The cached or newly loaded item.
     */
    suspend inline fun <reified T> get(
        bucket: String,
        key: String,
        ttl: Duration,
        noinline loader: suspend () -> T,
    ): T =
        getInternal(bucket, key, ttl, loader, serializer<T>())

    /**
     * Atomically invalidates all cached entries within a specific [bucket].
     *
     * Increments the bucket version stored in Valkey and evicts the local version key from L1 cache.
     * Subsequent read requests for keys in this bucket will construct new full keys based on the updated version.
     *
     * @param bucket The name of the bucket to invalidate.
     */
    suspend fun invalidate(bucket: String) {
        val versionKey = versionKey(bucket)
        valkeyWrapper.incr(versionKey)
        l1.remove(versionKey)
    }
}