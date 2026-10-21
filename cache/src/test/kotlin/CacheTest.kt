package fr.shikkanime.cache

import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Nested
    @DisplayName("tests for cache statistics")
    inner class CacheStatisticsTests {

        @Test
        suspend fun `should record an L1 hit without any put when the item is already in L1`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            val bucket = "episodes"
            val key = "stats-1"
            l1Cache.put("ver:$bucket", 0L)
            l1Cache.put("$bucket:0:$key", CachedEpisode(1, "Cached Episode"))

            // When
            cache.get<CachedEpisode>(bucket, key, 10.minutes) {
                CachedEpisode(1, "Loader Episode")
            }

            // Then
            assertCounters(cache.stats, l1Hits = 1)
            coVerify(exactly = 0) { valkeyWrapper.get(any()) }
        }

        @Test
        suspend fun `should record an L2 hit and one put when the item is served from Valkey`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>()
            val codec = BinaryCodec()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper, codec = codec)

            val bucket = "episodes"
            val key = "stats-2"
            val ttl = 10.minutes
            val episode = CachedEpisode(2, "Valkey Episode")

            coEvery { valkeyWrapper.get("ver:$bucket") } returns codec.encodeVersion(0L)
            coEvery {
                valkeyWrapper.get("$bucket:0:$key")
            } returns codec.encode(episode, CachedEpisode.serializer())

            // When
            cache.get<CachedEpisode>(bucket, key, ttl) {
                CachedEpisode(2, "Loader Episode")
            }

            // Then
            assertCounters(cache.stats, l2Hits = 1, puts = 1)
        }

        @Test
        suspend fun `should record a miss and two puts when both cache levels miss`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            val bucket = "episodes"
            val key = "stats-3"
            coEvery { valkeyWrapper.get(any()) } returns null

            // When
            cache.get<CachedEpisode>(bucket, key, 10.minutes) {
                CachedEpisode(3, "Freshly Loaded")
            }

            // Then
            assertCounters(cache.stats, misses = 1, puts = 2)
        }

        @Test
        suspend fun `should record a miss without any put when the loader fails`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            coEvery { valkeyWrapper.get(any()) } returns null

            // When
            val result = runCatching {
                cache.get<CachedEpisode>("episodes", "stats-4", 10.minutes) {
                    throw IllegalStateException("Loader failure")
                }
            }

            // Then
            assertTrue(result.isFailure)
            assertEquals("Loader failure", result.exceptionOrNull()?.message)
            assertCounters(cache.stats, misses = 1)
        }

        @Test
        suspend fun `should record one outcome for coalesced concurrent gets`(): Unit =
            coroutineScope {
                // Given
                val l1Cache = L1Cache()
                val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
                val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)
                l1Cache.put("ver:episodes", 0L)

                coEvery { valkeyWrapper.get(any()) } returns null

                val loaderStarted = CompletableDeferred<Unit>()
                val releaseLoader = CompletableDeferred<Unit>()
                val loaderCounter = AtomicInteger(0)

                val loader: suspend () -> CachedEpisode = {
                    loaderCounter.incrementAndGet()
                    loaderStarted.complete(Unit)
                    releaseLoader.await()
                    CachedEpisode(5, "Coalesced Episode")
                }

                // When
                val first = async {
                    cache.get<CachedEpisode>("episodes", "stats-5", 10.minutes, loader)
                }
                loaderStarted.await()

                // Runs synchronously up to the join on the in-flight computation, so the second
                // caller coalesces deterministically instead of racing a wall-clock delay
                val second = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.get<CachedEpisode>("episodes", "stats-5", 10.minutes, loader)
                }
                releaseLoader.complete(Unit)

                first.await()
                second.await()

                // Then
                assertEquals(1, loaderCounter.get())
                assertCounters(cache.stats, misses = 1, puts = 2)
            }

        @Test
        suspend fun `should record no outcome when the L2 lookup fails`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            val bucket = "episodes"
            val key = "stats-6"
            coEvery { valkeyWrapper.get("ver:$bucket") } returns null
            coEvery { valkeyWrapper.get("$bucket:0:$key") } throws IllegalStateException("L2 down")

            // When
            val result = runCatching {
                cache.get<CachedEpisode>(bucket, key, 10.minutes) {
                    CachedEpisode(6, "Loader Episode")
                }
            }

            // Then
            assertTrue(result.isFailure)
            assertEquals("L2 down", result.exceptionOrNull()?.message)
            assertCounters(cache.stats)
        }

        @Test
        suspend fun `should record no outcome when the L2 payload cannot be decoded`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>()
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            val bucket = "episodes"
            val key = "stats-7"
            coEvery { valkeyWrapper.get("ver:$bucket") } returns null
            coEvery { valkeyWrapper.get("$bucket:0:$key") } returns byteArrayOf(1)

            // When
            val result = runCatching {
                cache.get<CachedEpisode>(bucket, key, 10.minutes) {
                    CachedEpisode(7, "Loader Episode")
                }
            }

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SerializationException)
            assertCounters(cache.stats)
        }

        @Test
        suspend fun `should compute hit rate over the recorded outcomes`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            val bucket = "episodes"
            val key = "stats-8"
            coEvery { valkeyWrapper.get(any()) } returns null

            // When
            cache.get<CachedEpisode>(bucket, key, 10.minutes) {
                CachedEpisode(8, "Freshly Loaded")
            }
            cache.get<CachedEpisode>(bucket, key, 10.minutes) {
                CachedEpisode(8, "Loader Episode")
            }

            // Then
            assertCounters(cache.stats, l1Hits = 1, misses = 1, puts = 2)
            assertEquals(0.5, cache.stats.hitRate)
        }

        @Test
        suspend fun `should not record any outcome when the bucket is invalidated`() {
            // Given
            val l1Cache = L1Cache()
            val valkeyWrapper = mockk<ValkeyWrapper>(relaxed = true)
            val cache = Cache(l1 = l1Cache, valkeyWrapper = valkeyWrapper)

            coEvery { valkeyWrapper.incr("ver:episodes") } returns 1L

            // When
            cache.invalidate("episodes")

            // Then
            assertCounters(cache.stats)
        }
    }
}
