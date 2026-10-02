package fr.shikkanime.graphql

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting applied to the GraphQL endpoint.
 *
 * The bucket is token-based: each request consumes as many tokens as the estimated cost of its
 * query, so a heavy query drains the bucket faster than a light one. A flat per-request count
 * would let a single expensive document through as cheaply as a trivial one.
 *
 * @property enabled Whether the endpoint is rate limited at all.
 * @property limit Bucket capacity, expressed in cost units rather than requests.
 * @property refillPeriod How often the bucket is refilled to full.
 * @property minimumRequestWeight Weight charged when the query cost cannot be determined, so a
 * request is never free. Zero would let a caller bypass the limit entirely.
 */
data class RateLimitConfig(
    val enabled: Boolean = true,
    val limit: Int = 100,
    val refillPeriod: Duration = 1.minutes,
    val minimumRequestWeight: Int = 1
)

/**
 * Configuration of the GraphQL module.
 *
 * Every value here is a security decision rather than a cosmetic one: raising
 * [maxQueryComplexity] or [requestBodyLimit] widens the DoS surface, so a consumer that overrides
 * them should know what it is trading away. The defaults are conservative but generous enough for
 * a normal admin query; they are starting points to measure, not constants to trust.
 *
 * @property path Route the GraphQL endpoint is bound to. A missing leading slash is added.
 * @property maxQueryDepth Maximum selection nesting accepted before execution is refused.
 * @property maxQueryComplexity Maximum estimated cost accepted before execution is refused.
 * @property introspectionEnabled Whether the schema can be queried through introspection. Public
 * by default, matching the already-public Swagger UI.
 * @property rateLimit Rate limiting applied to the endpoint.
 * @property requestBodyLimit Maximum accepted request body size, measured on the decoded stream so
 * a compressed payload cannot exceed it after inflation.
 */
data class GraphQLConfig(
    val path: String = DEFAULT_PATH,
    val maxQueryDepth: Int = 8,
    val maxQueryComplexity: Int = 200,
    val introspectionEnabled: Boolean = true,
    val rateLimit: RateLimitConfig = RateLimitConfig(),
    val requestBodyLimit: Long = DEFAULT_REQUEST_BODY_LIMIT
) {
    /** [path] guaranteed to start with a slash. */
    val normalizedPath: String =
        if (path.startsWith("/")) path else "/$path"

    private companion object {
        private const val DEFAULT_PATH = "/graphql"
        private const val DEFAULT_REQUEST_BODY_LIMIT = 64L * 1024
    }
}
