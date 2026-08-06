package fr.shikkanime.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class L1CacheTest {

    @Nested
    @DisplayName("tests for entry storage and retrieval")
    inner class StorageTests {

        @Test
        fun `should return stored value when key exists and has not expired`() {
            // Given
            val l1Cache = L1Cache()
            val key = "item:1"
            val value = "Sample Payload"

            // When
            l1Cache.put(key, value)
            val result = l1Cache.get(key)

            // Then
            assertEquals(value, result)
        }

        @Test
        fun `should return null when key does not exist`() {
            // Given
            val l1Cache = L1Cache()

            // When
            val result = l1Cache.get("nonexistent")

            // Then
            assertNull(result)
        }

        @Test
        fun `should return null and evict entry when TTL has expired`() {
            // Given
            val l1Cache = L1Cache()
            val key = "ephemeral:1"
            val value = "Transient Data"

            // When
            l1Cache.put(key, value, 1.milliseconds)
            Thread.sleep(10)
            val result = l1Cache.get(key)

            // Then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("tests for eviction and capacity limits")
    inner class EvictionTests {

        @Test
        fun `should evict eldest accessed entry when maxSize is exceeded`() {
            // Given
            val maxSize = 3
            val l1Cache = L1Cache(maxSize = maxSize, defaultTtl = 60.seconds)

            // When
            l1Cache.put("k1", "v1")
            l1Cache.put("k2", "v2")
            l1Cache.put("k3", "v3")
            l1Cache.put("k4", "v4")

            // Then
            assertNull(l1Cache.get("k1"))
            assertEquals("v2", l1Cache.get("k2"))
            assertEquals("v3", l1Cache.get("k3"))
            assertEquals("v4", l1Cache.get("k4"))
        }

        @Test
        fun `should remove specific entry when remove is called`() {
            // Given
            val l1Cache = L1Cache()
            l1Cache.put("key1", "val1")

            // When
            l1Cache.remove("key1")

            // Then
            assertNull(l1Cache.get("key1"))
        }

        @Test
        fun `should clear all entries when clear is called`() {
            // Given
            val l1Cache = L1Cache()
            l1Cache.put("key1", "val1")
            l1Cache.put("key2", "val2")

            // When
            l1Cache.clear()

            // Then
            assertNull(l1Cache.get("key1"))
            assertNull(l1Cache.get("key2"))
        }
    }
}
