package fr.shikkanime.graphql

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import graphql.GraphQLContext
import org.dataloader.DataLoaderRegistry

/**
 * Builds the factory used when a consumer supplies no DataLoader.
 *
 * `KotlinDataLoaderRegistryFactory` is a final class, so the empty case is expressed by
 * instantiating it with no loader rather than by subclassing it. The no-arg constructor is the
 * library's own "no loaders" case, which keeps the module working out of the box for a project
 * with no N+1 problem.
 *
 * @return A factory generating an empty registry.
 */
fun noOpDataLoaderRegistryFactory(): KotlinDataLoaderRegistryFactory =
    KotlinDataLoaderRegistryFactory()

/**
 * Whether this factory carries at least one DataLoader.
 *
 * Lets the caller tell "the consumer configured batching" from "the consumer configured nothing",
 * which decides whether the N+1 mitigation is actually in place.
 */
fun KotlinDataLoaderRegistryFactory.hasLoaders(): Boolean =
    generate(GraphQLContext.newContext().build(), null).keys.isNotEmpty()

/**
 * Generates the DataLoader registry for one request.
 *
 * @param graphQLContext Context of the running operation, passed to each loader.
 * @return The registry holding every loader the consumer declared.
 */
internal fun KotlinDataLoaderRegistryFactory.generateRegistry(): DataLoaderRegistry =
    generate(GraphQLContext.newContext().build(), null)
