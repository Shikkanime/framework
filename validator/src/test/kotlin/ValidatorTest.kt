package fr.shikkanime.validator

import fr.shikkanime.validator.exceptions.ObjectNotValidException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ValidatorTest {

    data class SampleNotNullDto(
        @NotNull("Name cannot be null")
        val name: String?
    )

    data class SampleNotBlankDto(
        @NotBlank("Code cannot be blank")
        val code: String?
    )

    data class SampleNotEmptyDto(
        @NotEmpty("Items cannot be empty")
        val items: List<String>?
    )

    @RequireAtLeastOneValid("Provide at least one detail")
    data class SampleAtLeastOneDto(
        val fieldA: String?,
        val fieldB: String?
    )

    @Nested
    @DisplayName("tests for NotNull constraint")
    inner class NotNullTests {
        @Test
        fun `should pass validation when field is not null`() {
            // Given
            val dto = SampleNotNullDto(name = "Shikkanime")

            // When & Then
            assertDoesNotThrow { Validator.validate(dto) }
        }

        @Test
        fun `should throw exception when field is null`() {
            // Given
            val dto = SampleNotNullDto(name = null)

            // When & Then
            val exception = assertThrows(ObjectNotValidException::class.java) {
                Validator.validate(dto)
            }
            assertTrue(exception.message.contains("Name cannot be null"))
        }
    }

    @Nested
    @DisplayName("tests for NotBlank constraint")
    inner class NotBlankTests {
        @ParameterizedTest
        @ValueSource(strings = ["XYZ123", "Valid Code", "12345"])
        fun `should pass validation when string is not blank`(validCode: String) {
            // Given
            val dto = SampleNotBlankDto(code = validCode)

            // When & Then
            assertDoesNotThrow { Validator.validate(dto) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", "\t\n"])
        fun `should throw exception when string is blank or whitespace`(blankCode: String) {
            // Given
            val dto = SampleNotBlankDto(code = blankCode)

            // When & Then
            val exception = assertThrows(ObjectNotValidException::class.java) {
                Validator.validate(dto)
            }
            assertTrue(exception.message.contains("Code cannot be blank"))
        }
    }

    @Nested
    @DisplayName("tests for NotEmpty constraint")
    inner class NotEmptyTests {
        @Test
        fun `should pass validation when collection is non-empty`() {
            // Given
            val dto = SampleNotEmptyDto(items = listOf("item1"))

            // When & Then
            assertDoesNotThrow { Validator.validate(dto) }
        }

        @Test
        fun `should throw exception when collection is empty`() {
            // Given
            val dto = SampleNotEmptyDto(items = emptyList())

            // When & Then
            val exception = assertThrows(ObjectNotValidException::class.java) {
                Validator.validate(dto)
            }
            assertTrue(exception.message.contains("Items cannot be empty"))
        }
    }

    @Nested
    @DisplayName("tests for RequireAtLeastOneValid constraint")
    inner class RequireAtLeastOneValidTests {
        @Test
        fun `should pass validation when at least one field is non-null`() {
            // Given
            val dto = SampleAtLeastOneDto(fieldA = "value", fieldB = null)

            // When & Then
            assertDoesNotThrow { Validator.validate(dto) }
        }

        @Test
        fun `should throw exception when all fields are null`() {
            // Given
            val dto = SampleAtLeastOneDto(fieldA = null, fieldB = null)

            // When & Then
            val exception = assertThrows(ObjectNotValidException::class.java) {
                Validator.validate(dto)
            }
            assertTrue(exception.message.contains("Provide at least one detail"))
        }
    }
}
