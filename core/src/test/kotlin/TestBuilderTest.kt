package fr.shikkanime.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestBuilderTest {
    interface Sample

    class SampleBuilder : TestBuilder<Sample> {
        override fun build(): Sample =
            object : Sample {}
    }

    @Test
    fun `should produce the instance returned by the builder implementation`() {
        // Given
        val builder = SampleBuilder()

        // When
        val built = builder.build()

        // Then
        assertTrue(built is Sample)
    }
}
