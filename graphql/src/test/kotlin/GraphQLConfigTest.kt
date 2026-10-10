package fr.shikkanime.graphql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQLConfigTest {
    @Nested
    @DisplayName("Given the default configuration")
    inner class GivenDefaultConfiguration {
        private val config = GraphQLConfig()

        @Test
        fun `should expose a usable endpoint path`() {
            // Then
            assertTrue(config.path.isNotBlank())
        }

        @Test
        fun `should keep the security limits positive`() {
            // Then
            assertTrue(config.maxQueryDepth > 0)
            assertTrue(config.maxQueryComplexity > 0)
            assertTrue(config.requestBodyLimit > 0)
        }

        @Test
        fun `should leave introspection public`() {
            // Then
            assertTrue(config.introspectionEnabled)
        }

        @Test
        fun `should apply a rate limit by default`() {
            // Then
            assertTrue(config.rateLimit.enabled)
            assertTrue(config.rateLimit.limit > 0)
        }
    }

    @Nested
    @DisplayName("Given a custom configuration")
    inner class GivenCustomConfiguration {
        @Test
        fun `should override every value`() {
            // Given
            val config = GraphQLConfig(
                path = "/internal/graphql",
                maxQueryDepth = 3,
                maxQueryComplexity = 42,
                introspectionEnabled = false,
                requestBodyLimit = 1024,
                rateLimit = RateLimitConfig(limit = 5, enabled = false)
            )

            // Then
            assertEquals("/internal/graphql", config.path)
            assertEquals(3, config.maxQueryDepth)
            assertEquals(42, config.maxQueryComplexity)
            assertEquals(false, config.introspectionEnabled)
            assertEquals(1024L, config.requestBodyLimit)
            assertEquals(false, config.rateLimit.enabled)
            assertEquals(5, config.rateLimit.limit)
        }
    }

    @Nested
    @DisplayName("Given a path without a leading slash")
    inner class GivenPathWithoutLeadingSlash {
        @Test
        fun `should normalize it`() {
            // Given
            val config = GraphQLConfig(path = "graphql")

            // Then
            assertEquals("/graphql", config.normalizedPath)
            assertEquals("graphql", config.path, "the raw value must stay untouched")
        }
    }

    @Nested
    @DisplayName("Given a path with a leading slash")
    inner class GivenPathWithLeadingSlash {
        @Test
        fun `should keep it as-is`() {
            // Given
            val config = GraphQLConfig(path = "/internal/graphql")

            // Then
            assertEquals("/internal/graphql", config.normalizedPath)
        }
    }
}
