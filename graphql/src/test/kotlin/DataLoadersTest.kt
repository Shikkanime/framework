package fr.shikkanime.graphql

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataLoadersTest {
    // Fixtures first: everything the tests below rely on.
    private val pingLoader = object : KotlinDataLoader<String, String> {
        override val dataLoaderName = "PingDataLoader"

        override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, String> =
            DataLoaderFactory.newDataLoader<String, String>(
                { keys: List<String> -> CompletableFuture.completedFuture(keys) },
                DataLoaderOptions.newOptions().build()
            )
    }

    private fun registryOf(factory: KotlinDataLoaderRegistryFactory): DataLoaderRegistry =
        factory.generate(GraphQLContext.newContext().build(), null)

    @Nested
    @DisplayName("Given no DataLoader registered")
    inner class GivenNoDataLoader {
        @Test
        fun `should generate an empty registry without throwing`() {
            // Given
            val factory = noOpDataLoaderRegistryFactory()

            // When
            val registry = registryOf(factory)

            // Then
            assertTrue(registry.keys.isEmpty())
        }

        @Test
        fun `should report that it is not usable`() {
            // Given
            val factory = noOpDataLoaderRegistryFactory()

            // Then
            assertFalse(factory.hasLoaders())
        }
    }

    @Nested
    @DisplayName("Given a consumer registry factory")
    inner class GivenConsumerRegistryFactory {
        @Test
        fun `should expose the consumer loader`() {
            // Given
            val factory = KotlinDataLoaderRegistryFactory(pingLoader)

            // When
            val registry = registryOf(factory)

            // Then
            assertTrue(registry.keys.contains("PingDataLoader"))
        }

        @Test
        fun `should report that it carries loaders`() {
            // Given
            val factory = KotlinDataLoaderRegistryFactory(pingLoader)

            // Then
            assertTrue(factory.hasLoaders())
        }
    }

    @Nested
    @DisplayName("Given the default factory")
    inner class GivenDefaultFactory {
        @Test
        fun `should register no key at all`() {
            // The no-op factory must never register a key, or a resolver looking up a loader by
            // name would find nothing while the registry claims to be populated.
            // Given
            val factory = noOpDataLoaderRegistryFactory()

            // When
            val registry = registryOf(factory)

            // Then
            assertEquals(0, registry.keys.size)
        }
    }
}
