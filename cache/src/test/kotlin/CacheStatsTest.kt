package fr.shikkanime.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CacheStatsTest {

    @Nested
    @DisplayName("tests for counter recording")
    inner class CounterRecordingTests {

        @Test
        fun `should increment only the L1 hit counter when recording an L1 hit`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordL1Hit()

            // Then
            assertCounters(stats, l1Hits = 1)
        }

        @Test
        fun `should increment only the L2 hit counter when recording an L2 hit`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordL2Hit()

            // Then
            assertCounters(stats, l2Hits = 1)
        }

        @Test
        fun `should increment only the miss counter when recording a miss`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordMiss()

            // Then
            assertCounters(stats, misses = 1)
        }

        @Test
        fun `should increment only the put counter when recording a put`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordPut()

            // Then
            assertCounters(stats, puts = 1)
        }

        @Test
        fun `should accumulate counters monotonically across recordings`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordL1Hit()
            stats.recordL1Hit()
            stats.recordL2Hit()
            stats.recordMiss()
            stats.recordPut()
            stats.recordPut()
            stats.recordPut()

            // Then
            assertCounters(stats, l1Hits = 2, l2Hits = 1, misses = 1, puts = 3)
        }
    }

    @Nested
    @DisplayName("tests for hit rate computation")
    inner class HitRateTests {

        @Test
        fun `should return zero hit rate before any lookup is recorded`() {
            // Given
            val stats = CacheStats()

            // When & Then
            assertEquals(0.0, stats.hitRate)
        }

        @Test
        fun `should compute hit rate across both cache levels`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordL1Hit()
            stats.recordL2Hit()
            stats.recordMiss()
            stats.recordMiss()

            // Then
            assertCounters(stats, l1Hits = 1, l2Hits = 1, misses = 2)
            assertEquals(0.5, stats.hitRate)
        }

        @Test
        fun `should exclude puts from the hit rate ratio`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordL1Hit()
            stats.recordMiss()
            stats.recordPut()
            stats.recordPut()
            stats.recordPut()

            // Then
            assertCounters(stats, l1Hits = 1, misses = 1, puts = 3)
            assertEquals(0.5, stats.hitRate)
        }

        @Test
        fun `should return one when every recorded lookup is a hit`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordL1Hit()

            // Then
            assertCounters(stats, l1Hits = 1)
            assertEquals(1.0, stats.hitRate)
        }

        @Test
        fun `should return zero when every recorded lookup is a miss`() {
            // Given
            val stats = CacheStats()

            // When
            stats.recordMiss()
            stats.recordMiss()

            // Then
            assertCounters(stats, misses = 2)
            assertEquals(0.0, stats.hitRate)
        }
    }
}
