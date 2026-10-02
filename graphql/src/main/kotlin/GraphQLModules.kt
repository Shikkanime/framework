package fr.shikkanime.graphql

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.expediagroup.graphql.server.ktor.DefaultKtorGraphQLContextFactory
import com.expediagroup.graphql.server.ktor.GraphQL
import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.operations.Subscription
import fr.shikkanime.core.LoggerFactory
import java.util.logging.Logger
import graphql.language.Document
import graphql.parser.InvalidSyntaxException
import graphql.parser.Parser
import graphql.parser.exceptions.ParseCancelledTooDeepException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.intercept
import io.ktor.util.pipeline.PipelineContext
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Name of the rate limiter guarding the GraphQL endpoint. */
internal const val GRAPHQL_RATE_LIMIT_NAME = "graphql"

private val REQUEST_JSON = Json { ignoreUnknownKeys = true }

/**
 * Installs the GraphQL module: engine, schema, route, and the security stack guarding it.
 *
 * The order of the guards is deliberate. The body limit runs first, so an oversized payload never
 * reaches the parser. The document is then priced and charged to the rate limit bucket, so a heavy
 * query drains it faster than a light one. Only after that is the query executed.
 *
 * What the rate limit is **not**: a guard standing in front of the analysis. The weight comes from
 * a document that has already been parsed, so pricing happens after parsing, not before. It limits
 * sustained abuse, while the complexity and depth bounds refuse the single hostile query. Both are
 * needed — bounds alone let an attacker trickle expensive queries, and rate limiting alone never
 * notices that one query is a bomb.
 *
 * @property config Bounds and endpoint configuration.
 * @property dataLoaderRegistryFactory DataLoaders the consumer provides. The framework only wires
 * them: it has no repository and cannot know what to batch.
 * @property schemaQueries Queries exposed by the schema.
 * @property schemaMutations Mutations exposed by the schema.
 * @property schemaSubscriptions Subscriptions exposed by the schema. Websocket transport is not
 * enabled by this module, so declaring one would advertise an endpoint that cannot serve it.
 * @property schemaPackages Packages scanned for annotated types. The generator refuses to build
 * a schema without one, so it is required even though listing the queries explicitly is enough.
 */
fun Application.configureGraphQL(
    config: GraphQLConfig = GraphQLConfig(),
    dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactory = noOpDataLoaderRegistryFactory(),
    schemaPackages: List<String>,
    schemaQueries: List<Query> = emptyList(),
    schemaMutations: List<Mutation> = emptyList(),
    schemaSubscriptions: List<Subscription> = emptyList()
) {
    val logger = LoggerFactory.getLogger()
    val limits = config.toSecurityLimits()

    // Measured on the decoded stream, so a compressed payload cannot inflate past the limit.
    install(RequestBodyLimit) {
        bodyLimit { config.requestBodyLimit }
    }

    install(Compression) {
        gzip()
    }

    install(GraphQL) {
        schema {
            packages = schemaPackages
            queries = schemaQueries
            mutations = schemaMutations
            subscriptions = schemaSubscriptions
        }

        engine {
            this.dataLoaderRegistryFactory = dataLoaderRegistryFactory
        }

        server {
            this.contextFactory = DefaultKtorGraphQLContextFactory()
        }
    }

    if (config.rateLimit.enabled) {
        install(RateLimit) {
            register(RateLimitName(GRAPHQL_RATE_LIMIT_NAME)) {
                rateLimiter(limit = config.rateLimit.limit, refillPeriod = config.rateLimit.refillPeriod)
                requestKey { call -> call.request.local.remoteHost }
                requestWeight { call, _ -> call.graphqlWeight(config, limits, logger) }
            }
        }
    }

    routing {
        rateLimit(RateLimitName(GRAPHQL_RATE_LIMIT_NAME)) {
            // The plugin registers its own POST handler, so a sibling `post { }` would be tried
            // first only by luck of registration order. The guard is therefore an interception on
            // the route, which runs before any handler: a document that breaks the bounds is
            // answered here and the engine never sees it.
            intercept(ApplicationCallPipeline.Call) {
                val call = context
                val requestPath = call.request.local.uri.substringBefore('?')

                if (!isGraphQLPath(requestPath, config.normalizedPath)) {
                    proceed()
                    return@intercept
                }

                val document = parseOrNull(call.receiveText(), logger)

                if (document != null && exceedsLimits(document, limits)) {
                    respondRefusal(refusalMessage(document, limits))
                    finish()
                } else {
                    proceed()
                }
            }

            route(config.normalizedPath) {
                // requestWeight reads the body to price the query, which would otherwise consume
                // the one-shot request stream and leave the GraphQL handler with an empty body.
                // The in-memory cache suits a GraphQL body: small and short-lived, so a file cache
                // would be pure overhead.
                install(DoubleReceive) {
                    maxSize(config.requestBodyLimit)
                    useFileForCache { false }
                }
            }

            graphQLPostRoute(config.normalizedPath)
        }
    }
}

/**
 * Whether the request targets the GraphQL endpoint.
 *
 * Compared on the exact path rather than a prefix, so a sibling route such as `/graphqli` is not
 * dragged through the GraphQL guards.
 */
private fun isGraphQLPath(requestPath: String, graphqlPath: String): Boolean =
    requestPath == graphqlPath || requestPath == "$graphqlPath/"

/** Bounds derived from the endpoint configuration. */
internal fun GraphQLConfig.toSecurityLimits(): SecurityLimits =
    SecurityLimits(
        maxQueryDepth = maxQueryDepth,
        maxQueryComplexity = maxQueryComplexity,
        introspectionEnabled = introspectionEnabled
    )

/**
 * Answers a refused document.
 *
 * The body is a GraphQL `errors` array so a GraphQL client parses it like any other GraphQL
 * response. The status is 400 rather than the 200 GraphQL uses for execution errors: this document
 * never reached the engine, so it is a rejected request, not a failed execution — and a caller
 * branching on the status code must be able to tell the two apart.
 */
private suspend fun PipelineContext<Unit, PipelineCall>.respondRefusal(message: String) {
    context.respondText(
        text = buildString {
            append("""{"errors":[{"message":""")
            append(REQUEST_JSON.encodeToString(JsonPrimitive(message).toString()))
            append("}]}")
        },
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.BadRequest
    )
}

/**
 * Cost charged to the rate limit bucket for one request.
 *
 * A document that cannot be priced — a batch array, invalid syntax, or a query too deep for the
 * parser — is charged the whole bucket, so no caller gets cheap work by sending something
 * unpriceable. A document that breaks the bounds is charged the whole bucket too: refusing it is
 * far cheaper than running it, and the caller should feel the difference. The weight is never zero,
 * because a zero-weight request is unlimited — worse than having no rate limit at all.
 */
private suspend fun ApplicationCall.graphqlWeight(
    config: GraphQLConfig,
    limits: SecurityLimits,
    logger: Logger
): Int {
    val body = readCachedText() ?: return config.rateLimit.minimumRequestWeight
    val document = parseOrNull(body, logger)

    if (document == null || exceedsLimits(document, limits)) {
        return maxOf(config.rateLimit.minimumRequestWeight, config.rateLimit.limit)
    }

    return estimateCost(document).coerceAtLeast(config.rateLimit.minimumRequestWeight)
}

/** Reads the cached body, which DoubleReceive made re-readable. */
private suspend fun ApplicationCall.readCachedText(): String? =
    runCatching { receiveText() }.getOrNull()

/**
 * Parses the query carried by a GraphQL request body.
 *
 * Returns null for a batch request, an unparseable body, or a body carrying no query. Each of
 * those is charged the maximum weight upstream, so this function only has to be honest about
 * failure, not about the consequence.
 */
private fun parseOrNull(body: String, logger: Logger): Document? {
    if (isBatchRequest(body)) {
        return null
    }

    val query = body.extractQuery() ?: return null

    return try {
        Parser().parseDocument(query)
    } catch (exception: InvalidSyntaxException) {
        logger.warning("Refusing a malformed graphql document: ${exception.message}")
        null
    } catch (exception: ParseCancelledTooDeepException) {
        logger.warning("Refusing a graphql document too deep to parse")
        null
    }
}

/** Message describing why a document was refused, safe to return to a client. */
private fun refusalMessage(document: Document, limits: SecurityLimits): String {
    return if (estimateCost(document) > limits.maxQueryComplexity) {
        "Query refused: its estimated cost exceeds the maximum of ${limits.maxQueryComplexity}"
    } else {
        "Query refused: its nesting depth exceeds the maximum of ${limits.maxQueryDepth}"
    }
}

/**
 * Reads the `query` member of a GraphQL request body.
 *
 * @return The query text, or null when the body is not a JSON object carrying one.
 */
internal fun String.extractQuery(): String? {
    val queryField = runCatching {
        REQUEST_JSON.parseToJsonElement(this).jsonObject["query"]
    }.getOrNull() ?: return null

    return runCatching { queryField.jsonPrimitive.content }.getOrNull()
}
