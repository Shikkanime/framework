package fr.shikkanime

import glide.api.GlideClient
import glide.api.models.GlideString
import glide.api.models.commands.SetOptions
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import kotlinx.coroutines.future.await
import kotlin.time.Duration

class ValkeyWrapperImpl(private val client: GlideClient) : ValkeyWrapper {
    private fun String.toGlideString(): GlideString =
        GlideString.of(this)

    private fun ByteArray.toGlideString(): GlideString =
        GlideString.of(this)

    override suspend fun get(key: String): ByteArray? =
        client.get(key.toGlideString())
            .await()
            ?.bytes

    override suspend fun set(key: String, value: ByteArray, ttl: Duration) {
        client.set(
            key.toGlideString(),
            value.toGlideString(),
            SetOptions.builder()
                .expiry(SetOptions.Expiry.Seconds(ttl.inWholeSeconds))
                .build()
        ).await()
    }

    override suspend fun incr(key: String): Long =
        client.incr(key.toGlideString())
            .await()

    companion object {
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