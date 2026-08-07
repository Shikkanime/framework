package fr.shikkanime.cache

import glide.api.GlideClient
import glide.api.models.GlideString
import glide.api.models.commands.SetOptions
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import kotlinx.coroutines.future.await
import kotlin.time.Duration

/**
 * Implementation of [ValkeyWrapper] powered by AWS Glide Redis/Valkey client.
 *
 * Interacts asynchronously with the underlying [GlideClient] to perform binary GET, SET with TTL,
 * and atomic INCR operations.
 *
 * @param client The active [GlideClient] instance.
 */
class ValkeyWrapperImpl(private val client: GlideClient) : ValkeyWrapper {
    /**
     * Converts a string key to AWS Glide's internal [GlideString] representation.
     *
     * @return The [GlideString] instance.
     */
    private fun String.toGlideString(): GlideString =
        GlideString.of(this)

    /**
     * Converts a raw byte array payload to AWS Glide's internal [GlideString] representation.
     *
     * @return The [GlideString] instance wrapping the byte array.
     */
    private fun ByteArray.toGlideString(): GlideString =
        GlideString.of(this)

    /**
     * Retrieves raw byte array stored at [key] via [GlideClient].
     *
     * @param key Entry key identifier.
     * @return Raw byte array, or `null` if key does not exist.
     */
    override suspend fun get(key: String): ByteArray? =
        client.get(key.toGlideString())
            .await()
            ?.bytes

    /**
     * Stores binary payload at [key] with expiration in seconds via [GlideClient].
     *
     * @param key Entry key identifier.
     * @param value Raw byte array payload.
     * @param ttl Expiration duration.
     */
    override suspend fun set(key: String, value: ByteArray, ttl: Duration) {
        client.set(
            key.toGlideString(),
            value.toGlideString(),
            SetOptions.builder()
                .expiry(SetOptions.Expiry.Seconds(ttl.inWholeSeconds))
                .build()
        ).await()
    }

    /**
     * Atomically increments integer value at [key] via [GlideClient].
     *
     * @param key Entry key identifier.
     * @return Increment result as [Long].
     */
    override suspend fun incr(key: String): Long =
        client.incr(key.toGlideString())
            .await()

    companion object {
        /**
         * Connects asynchronously to a Valkey / Redis instance using [GlideClientConfiguration].
         *
         * @param host Hostname or IP address of the Valkey server (defaults to `"localhost"`).
         * @param port Network port of the Valkey server (defaults to `6379`).
         * @return A newly initialized and connected [ValkeyWrapperImpl].
         */
        suspend fun connect(host: String = "localhost", port: Int = 6379): ValkeyWrapperImpl {
            val configuration = GlideClientConfiguration.builder()
                .address(
                    NodeAddress.builder()
                        .host(host)
                        .port(port)
                        .build()
                ).build()
            val client = GlideClient.createClient(configuration).await()
            return ValkeyWrapperImpl(client)
        }
    }
}