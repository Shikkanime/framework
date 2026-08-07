package fr.shikkanime.cache

import glide.api.GlideClient
import glide.api.models.GlideString
import glide.api.models.commands.SetOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes

class ValkeyWrapperImplTest {

    @Nested
    @DisplayName("tests for Valkey operations using GlideClient")
    inner class GlideClientOperationsTests {

        @Test
        suspend fun `should get byte array from GlideClient when key exists`() {
            // Given
            val glideClient = mockk<GlideClient>()
            val wrapper = ValkeyWrapperImpl(glideClient)
            val expectedBytes = "hello".toByteArray()
            val glideStringMock = mockk<GlideString>()
            every { glideStringMock.bytes } returns expectedBytes

            every { glideClient.get(any<GlideString>()) } returns CompletableFuture.completedFuture(glideStringMock)

            // When
            val result = wrapper.get("testKey")

            // Then
            assertArrayEquals(expectedBytes, result)
            verify(exactly = 1) { glideClient.get(match<GlideString> { it.bytes.contentEquals("testKey".toByteArray()) }) }
        }

        @Test
        suspend fun `should return null when key does not exist in GlideClient`() {
            // Given
            val glideClient = mockk<GlideClient>()
            val wrapper = ValkeyWrapperImpl(glideClient)

            every { glideClient.get(any<GlideString>()) } returns CompletableFuture.completedFuture(null)

            // When
            val result = wrapper.get("missingKey")

            // Then
            assertNull(result)
        }

        @Test
        suspend fun `should set binary value with expiry TTL on GlideClient`() {
            // Given
            val glideClient = mockk<GlideClient>()
            val wrapper = ValkeyWrapperImpl(glideClient)
            val key = "cacheKey"
            val value = "cacheValue".toByteArray()
            val ttl = 5.minutes

            val optionsSlot = slot<SetOptions>()
            every { glideClient.set(any<GlideString>(), any<GlideString>(), capture(optionsSlot)) } returns CompletableFuture.completedFuture("OK")

            // When
            wrapper.set(key, value, ttl)

            // Then
            verify(exactly = 1) {
                glideClient.set(
                    match<GlideString> { it.bytes.contentEquals(key.toByteArray()) },
                    match<GlideString> { it.bytes.contentEquals(value) },
                    any<SetOptions>()
                )
            }
        }

        @Test
        suspend fun `should increment key on GlideClient and return updated version`() {
            // Given
            val glideClient = mockk<GlideClient>()
            val wrapper = ValkeyWrapperImpl(glideClient)
            val key = "ver:bucket"

            every { glideClient.incr(any<GlideString>()) } returns CompletableFuture.completedFuture(42L)

            // When
            val version = wrapper.incr(key)

            // Then
            assertEquals(42L, version)
            verify(exactly = 1) { glideClient.incr(match<GlideString> { it.bytes.contentEquals(key.toByteArray()) }) }
        }
    }
}
