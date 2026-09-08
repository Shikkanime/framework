package fr.shikkanime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestBuilderTest {
    interface Sample

    class SampleImpl : Sample

    class SampleBuilder(private val instance: Sample) : TestBuilder<Sample> {
        override fun build(): Sample =
            instance
    }

    @Test
    fun `should produce the instance configured on the builder`() {
        // Given
        val expected = SampleImpl()
        val builder = SampleBuilder(expected)

        // When
        val built = builder.build()

        // Then
        assertEquals(expected, built)
    }
}
