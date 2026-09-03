package fr.shikkanime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StringUtilsTest {
    companion object {
        private const val LENGTH = 10
    }

    @Nested
    @DisplayName("tests for generateRandomString")
    inner class GenerateRandomStringTests {
        @Test
        fun `should generate string with requested length using the complete alphabet by default`() {
            // Given / When
            val result = StringUtils.generateRandomString(LENGTH)
            val expectedAlphabet = StringUtils.ALPHABET_UPPERCASE +
                    StringUtils.ALPHABET_LOWERCASE +
                    StringUtils.ALPHABET_NUMBERS +
                    StringUtils.ALPHABET_SPECIAL

            // Then
            assertEquals(LENGTH, result.length)
            assertTrue(result.all(expectedAlphabet::contains))
        }

        @Test
        fun `should generate only alphanumeric characters when special characters are excluded`() {
            // Given / When
            val result = StringUtils.generateRandomString(LENGTH, includeSpecial = false)
            val expectedAlphabet = StringUtils.ALPHABET_UPPERCASE +
                    StringUtils.ALPHABET_LOWERCASE +
                    StringUtils.ALPHABET_NUMBERS

            // Then
            assertTrue(result.all(expectedAlphabet::contains))
        }

        @Test
        fun `should include every required character type`() {
            // Given / When
            val result = StringUtils.generateRandomString(
                length = LENGTH,
                shouldHaveAtLeastOneUppercase = true,
                shouldHaveAtLeastOneLowercase = true,
                shouldHaveAtLeastOneNumber = true,
                shouldHaveAtLeastOneSpecial = true
            )

            // Then
            assertAll(
                { assertTrue(result.any(StringUtils.ALPHABET_UPPERCASE::contains)) },
                { assertTrue(result.any(StringUtils.ALPHABET_LOWERCASE::contains)) },
                { assertTrue(result.any(StringUtils.ALPHABET_NUMBERS::contains)) },
                { assertTrue(result.any(StringUtils.ALPHABET_SPECIAL::contains)) }
            )
        }

        @Test
        fun `should generate one character of every required type at minimum valid length`() {
            // Given / When
            val result = StringUtils.generateRandomString(
                length = 4,
                shouldHaveAtLeastOneUppercase = true,
                shouldHaveAtLeastOneLowercase = true,
                shouldHaveAtLeastOneNumber = true,
                shouldHaveAtLeastOneSpecial = true
            )

            // Then
            assertAll(
                { assertEquals(4, result.length) },
                { assertEquals(1, result.count(StringUtils.ALPHABET_UPPERCASE::contains)) },
                { assertEquals(1, result.count(StringUtils.ALPHABET_LOWERCASE::contains)) },
                { assertEquals(1, result.count(StringUtils.ALPHABET_NUMBERS::contains)) },
                { assertEquals(1, result.count(StringUtils.ALPHABET_SPECIAL::contains)) }
            )
        }

        @Test
        fun `should reject special character requirement when special characters are excluded`() {
            // Given / When
            val exception = assertThrows(IllegalArgumentException::class.java) {
                StringUtils.generateRandomString(
                    length = LENGTH,
                    includeSpecial = false,
                    shouldHaveAtLeastOneSpecial = true
                )
            }

            // Then
            assertEquals(
                "Cannot require at least one special character when special characters are not included",
                exception.message
            )
        }

        @ParameterizedTest
        @ValueSource(ints = [-1, 0])
        fun `should reject non-positive lengths`(length: Int) {
            // Given / When
            val exception = assertThrows(IllegalArgumentException::class.java) {
                StringUtils.generateRandomString(length)
            }

            // Then
            assertEquals("Length must be greater than 0", exception.message)
        }

        @Test
        fun `should reject length smaller than number of required character types`() {
            // Given / When
            val exception = assertThrows(IllegalArgumentException::class.java) {
                StringUtils.generateRandomString(
                    length = 2,
                    shouldHaveAtLeastOneUppercase = true,
                    shouldHaveAtLeastOneLowercase = true,
                    shouldHaveAtLeastOneNumber = true
                )
            }

            // Then
            assertEquals("Length must be greater than or equal to the number of required character types", exception.message)
        }
    }
}