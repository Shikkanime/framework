package fr.shikkanime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Cache(
    private val l1: L1Cache = L1Cache(),
    private val valkeyWrapper: ValkeyWrapper,
    private val codec: BinaryCodec = BinaryCodec(),
    private val singleFlight: SingleFlight = SingleFlight(),
    private val versionCacheTtl: Duration = 30.seconds
) {
    private fun versionKey(bucket: String): String =
        "ver:$bucket"

    private fun fullKey(bucket: String, version: Long, key: String): String =
        "$bucket:$version:$key"

    private suspend fun readVersion(bucket: String): Long {
        val cacheKey = versionKey(bucket)
        l1.get(cacheKey)?.let { return it as Long }
        val version = valkeyWrapper.get(cacheKey)
            ?.let { codec.decodeVersion(it) }
            ?: 0
        l1.put(cacheKey, version, versionCacheTtl)
        return version
    }

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

    suspend inline fun <reified T> get(
        bucket: String,
        key: String,
        ttl: Duration,
        noinline loader: suspend () -> T,
    ): T =
        getInternal(bucket, key, ttl, loader, serializer<T>())

    suspend fun invalidate(bucket: String) {
        val versionKey = versionKey(bucket)
        valkeyWrapper.incr(versionKey)
        l1.remove(versionKey)
    }
}