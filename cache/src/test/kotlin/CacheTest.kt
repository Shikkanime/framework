package fr.shikkanime.cache

import io.mockk.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class CacheTest {

    @Serializable
    private data class CachedEpisode(val id: Int, val title: String)

    @Nested
    @DisplayName("tests for two-level cache retrieval and caching flow")
    inner class CacheRetrievalTests {

        @Test
        suspend fun `should return item from L1 cache without calling Valkey or loader`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>()
            val codec = BinaryCodec()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper, codec = codec)

            val bucket = "episodes"
            val key = "1"
            val ttl = 10.minutes
            val episode = CachedEpisode(1, "Pilot Episode")

            // Pre-seed version (0) and full key in L1
            l1Cache.put("ver:$bucket", 0L)
            l1Cache.put("$bucket:0:$key", episode)

            val loaderCounter = AtomicInteger(0)

            // When
            val result = cache.get<CachedEpisode>(bucket, key, ttl) {
                loaderCounter.incrementAndGet()
                CachedEpisode(1, "Loader Episode")
            }

            // Then
            assertEquals(episode, result)
            assertEquals(0, loaderCounter.get())
            coVerify(exactly = 0) { valkeyWrapper.get(any()) }
        }

        @Test
        suspend fun `should return item from Valkey L2 cache when L1 misses and update L1`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>()
            val codec = BinaryCodec()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper, codec = codec)

            val bucket = "episodes"
            val key = "2"
            val ttl = 10.minutes
            val episode = CachedEpisode(2, "Valkey Episode")
            val serializedBytes = codec.encode(episode, CachedEpisode.serializer())

            coEvery { valkeyWrapper.get("ver:$bucket") } returns null
            coEvery { valkeyWrapper.get("$bucket:0:$key") } returns serializedBytes

            val loaderCounter = AtomicInteger(0)

            // When
            val result = cache.get<CachedEpisode>(bucket, key, ttl) {
                loaderCounter.incrementAndGet()
                CachedEpisode(2, "Loader Episode")
            }

            // Then
            assertEquals(episode, result)
            assertEquals(0, loaderCounter.get())
            assertEquals(episode, l1Cache.get("$bucket:0:$key"))
            coVerify(exactly = 1) { valkeyWrapper.get("$bucket:0:$key") }
        }

        @Test
        suspend fun `should execute loader and store result in L1 and L2 when both caches miss`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val codec = BinaryCodec()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper, codec = codec)

            val bucket = "episodes"
            val key = "3"
            val ttl = 10.minutes
            val loadedEpisode = CachedEpisode(3, "Freshly Loaded")

            coEvery { valkeyWrapper.get(any()) } returns null

            val loaderCounter = AtomicInteger(0)

            // When
            val result = cache.get<CachedEpisode>(bucket, key, ttl) {
                loaderCounter.incrementAndGet()
                loadedEpisode
            }

            // Then
            assertEquals(loadedEpisode, result)
            assertEquals(1, loaderCounter.get())
            assertEquals(loadedEpisode, l1Cache.get("$bucket:0:$key"))
            coVerify(exactly = 1) { valkeyWrapper.set("$bucket:0:$key", any(), ttl) }
        }
    }

    @Nested
    @DisplayName("tests for bucket invalidation and versioning")
    inner class InvalidationTests {

        @Test
        suspend fun `should increment bucket version in Valkey and purge L1 version key upon invalidation`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            val bucket = "animes"
            l1Cache.put("ver:$bucket", 1L)

            coEvery { valkeyWrapper.incr("ver:$bucket") } returns 2L

            // When
            cache.invalidate(bucket)

            // Then
            assertNull(l1Cache.get("ver:$bucket"))
            coVerify(exactly = 1) { valkeyWrapper.incr("ver:$bucket") }
        }

        @Test
        suspend fun `should use updated bucket version after invalidation`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val codec = BinaryCodec()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper, codec = codec)

            val bucket = "manga"
            val key = "chapter-1"
            val ttl = 5.minutes

            // First lookup - initial version 0
            coEvery { valkeyWrapper.get("ver:$bucket") } returns null
            coEvery { valkeyWrapper.get("$bucket:0:$key") } returns null

            cache.get<String>(bucket, key, ttl) { "Chapter 1 Initial" }

            // Invalidate bucket - version becomes 1
            coEvery { valkeyWrapper.incr("ver:$bucket") } returns 1L
            cache.invalidate(bucket)

            // Setup Valkey to return version 1 encoded bytes
            val version1Bytes = codec.encodeVersion(1L)
            coEvery { valkeyWrapper.get("ver:$bucket") } returns version1Bytes
            coEvery { valkeyWrapper.get("$bucket:1:$key") } returns null

            val loaderCounter = AtomicInteger(0)

            // When
            val result = cache.get<String>(bucket, key, ttl) {
                loaderCounter.incrementAndGet()
                "Chapter 1 Updated"
            }

            // Then
            assertEquals("Chapter 1 Updated", result)
            assertEquals(1, loaderCounter.get())
            coVerify(exactly = 1) { valkeyWrapper.set("$bucket:1:$key", any(), ttl) }
        }
    }
}
