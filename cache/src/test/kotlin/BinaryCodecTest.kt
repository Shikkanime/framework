package fr.shikkanime.cache

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BinaryCodecTest {

    @Serializable
    private data class TestDto(val id: Long, val name: String, val active: Boolean)

    @Nested
    @DisplayName("tests for object binary encoding and decoding")
    inner class EncodingDecodingTests {

        @Test
        fun `should encode and decode serializable object correctly`() {
            // Given
            val codec = BinaryCodec()
            val original = TestDto(id = 42L, name = "Shikkanime Framework", active = true)

            // When
            val encoded = codec.encode(original, TestDto.serializer())
            val decoded = codec.decode(encoded, TestDto.serializer())

            // Then
            assertTrue(encoded.isNotEmpty())
            assertEquals(original, decoded)
        }

        @ParameterizedTest
        @ValueSource(strings = ["episodes", "anime_list", "user_session", ""])
        fun `should encode and decode string values accurately`(input: String) {
            // Given
            val codec = BinaryCodec()
            val serializer = kotlinx.serialization.serializer<String>()

            // When
            val encoded = codec.encode(input, serializer)
            val decoded = codec.decode(encoded, serializer)

            // Then
            assertEquals(input, decoded)
        }
    }

    @Nested
    @DisplayName("tests for version number byte conversion")
    inner class VersionEncodingTests {

        @ParameterizedTest
        @ValueSource(longs = [0L, 1L, 42L, 999999L, Long.MAX_VALUE])
        fun `should encode and decode long version numbers accurately`(version: Long) {
            // Given
            val codec = BinaryCodec()

            // When
            val bytes = codec.encodeVersion(version)
            val decodedVersion = codec.decodeVersion(bytes)

            // Then
            assertEquals(Long.SIZE_BYTES, bytes.size)
            assertEquals(version, decodedVersion)
        }
    }
}
