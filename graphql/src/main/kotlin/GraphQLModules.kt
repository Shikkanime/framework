package fr.shikkanime.graphql

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.expediagroup.graphql.server.ktor.DefaultKtorGraphQLContextFactory
import com.expediagroup.graphql.server.ktor.GraphQL
import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.operations.Subscription
import fr.shikkanime.core.LoggerFactory
import java.util.logging.Level
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
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
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

private const val BATCH_REFUSED = "Batch requests are not supported by this endpoint"
private const val UNREADABLE_REFUSED = "Request refused: the GraphQL document could not be read"
private const val INTROSPECTION_REFUSED = "Query refused: schema introspection is disabled"

private val REQUEST_JSON = Json { ignoreUnknownKeys = true }

/** Logger for the module's own diagnostics, outside the application lifecycle. */
private val MODULE_LOGGER = LoggerFactory.getLogger()

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
 * @property rateLimitKey Identifies the bucket each request is charged to. Defaults to the caller's
 * address, the only identity this module can know since it owns no authentication. A consumer
 * behind a shared address should pass its own key, or one client spends everyone's budget.
 */
fun Application.configureGraphQL(
    config: GraphQLConfig = GraphQLConfig(),
    dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactory = noOpDataLoaderRegistryFactory(),
    schemaPackages: List<String>,
    schemaQueries: List<Query> = emptyList(),
    schemaMutations: List<Mutation> = emptyList(),
    schemaSubscriptions: List<Subscription> = emptyList(),
    rateLimitKey: (ApplicationCall) -> Any = defaultRateLimitKey
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
                requestKey(rateLimitKey)
                requestWeight { call, _ -> call.graphqlWeight(config, limits, logger) }
            }
        }
    }

    routing {
        // `rateLimit` is applied only when the plugin is installed: Ktor throws at startup when a
        // route references a limiter that was never registered, so calling it unconditionally
        // would make `rateLimit.enabled = false` a boot-time crash rather than an opt-out.
        if (config.rateLimit.enabled) {
            rateLimit(RateLimitName(GRAPHQL_RATE_LIMIT_NAME)) {
                bindGraphQLRoute(config, limits, logger)
            }
        } else {
            bindGraphQLRoute(config, limits, logger)
        }
    }
}

/**
 * Registers the guarded GraphQL route on the current receiver.
 *
 * Kept out of [configureGraphQL] so the same body serves both the rate-limited and the opt-out
 * branch, instead of being duplicated across the two paths.
 */
private fun Route.bindGraphQLRoute(
    config: GraphQLConfig,
    limits: SecurityLimits,
    logger: Logger
) {
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

            val carried = call.carriedQuery()

            if (carried.isBatch) {
                respondRefusal(BATCH_REFUSED)
                finish()
                return@intercept
            }

            val document = parseOrNull(carried, logger)

            when {
                // An unparseable document must not reach the engine: it is exactly the shape an
                // attacker uses to slip past a check that only understands valid syntax.
                document == null -> {
                    respondRefusal(UNREADABLE_REFUSED)
                    finish()
                }

                exceedsLimits(document, limits) -> {
                    respondRefusal(refusalMessage(document, limits))
                    finish()
                }

                else -> proceed()
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
 * A document that cannot be priced — a batch array, invalid syntax, a query too deep for the
 * parser, or one that breaks the bounds — is charged [RateLimitConfig.unpriceableRequestWeight], a
 * moderate fixed penalty rather than the whole bucket. The floor matters most: the weight is never
 * zero, because a zero-weight request is unlimited and worse than having no rate limit at all. The
 * ceiling matters just as much: charging a refusal the whole bucket would hand an attacker a free
 * denial of service, since a few hundred bytes of malformed input would lock out every client
 * behind the same address for a whole refill period.
 *
 * A priced document pays its real cost, clamped to what the bucket can absorb.
 */
private suspend fun ApplicationCall.graphqlWeight(
    config: GraphQLConfig,
    limits: SecurityLimits,
    logger: Logger
): Int {
    val carried = carriedQuery()
    val document = parseOrNull(carried, logger)

    if (carried.isBatch || document == null || exceedsLimits(document, limits)) {
        return config.rateLimit.unpriceableRequestWeight
            .coerceAtLeast(config.rateLimit.minimumRequestWeight)
            .coerceAtMost(config.rateLimit.limit)
    }

    val cost = estimateCost(document, limits)

    return cost.toRateLimitWeight(
        minimum = config.rateLimit.minimumRequestWeight,
        maximum = config.rateLimit.limit
    )
}

/**
 * Reads the cached body, which DoubleReceive made re-readable.
 *
 * An oversized body is not an unreadable one: [io.ktor.server.plugins.PayloadTooLargeException]
 * means the request broke the body limit, and answering "could not be read" would send an operator
 * hunting for a parsing bug that does not exist. It is re-thrown so the platform answers 413.
 */
private suspend fun ApplicationCall.readCachedText(): String? =
    try {
        receiveText()
    } catch (exception: PayloadTooLargeException) {
        throw exception
    } catch (exception: Exception) {
        MODULE_LOGGER.log(Level.FINE, "Could not read the request body", exception)

        null
    }

/**
 * Parses the query carried by a GraphQL request body.
 *
 * Returns null for a batch request, an unparseable body, or a body carrying no query. Each of
 * those is charged the maximum weight upstream, so this function only has to be honest about
 * failure, not about the consequence.
 */
private fun parseOrNull(carried: CarriedQuery, logger: Logger): Document? {
    if (carried.isBatch) {
        logger.warning("Refusing a graphql batch request")
        return null
    }

    val query = (carried as? CarriedQuery.Single)?.query ?: return null

    return try {
        Parser().parseDocument(query)
    } catch (exception: InvalidSyntaxException) {
        logger.warning("Refusing a malformed graphql document")
        null
    } catch (exception: ParseCancelledTooDeepException) {
        logger.warning("Refusing a graphql document too deep to parse")
        null
    }
}

/**
 * The query a request carries, and whether it is a batch.
 *
 * A batch is not a document this module can price: graphql-java would execute every element as a
 * separate operation, so a batch would slip past a check that only ever looks at one parsed
 * document. It is therefore never allowed through — see [isBatch].
 */
internal sealed interface CarriedQuery {
    /** One query, ready to be parsed. */
    data class Single(val query: String) : CarriedQuery

    /** A batch array, which this module refuses. */
    data object Batch : CarriedQuery

    /** A request carrying no query at all. */
    data object None : CarriedQuery

    /** Whether this request is a batch array. */
    val isBatch: Boolean
        get() = this is Batch
}

/**
 * Reads the query a call carries, from its body.
 *
 * Only the body: this module registers the POST route alone, so a GET is answered with 405 by Ktor
 * before reaching any guard. Reading the query string as well would price a request the plugin
 * never serves, and would keep two code paths alive for one.
 */
internal suspend fun ApplicationCall.carriedQuery(): CarriedQuery {
    val body = readCachedText()

    if (body.isNullOrBlank()) {
        return CarriedQuery.None
    }

    if (isBatchRequest(body)) {
        return CarriedQuery.Batch
    }

    val query = body.extractQuery() ?: return CarriedQuery.None

    return CarriedQuery.Single(query)
}

/**
 * Message describing why a document was refused, safe to return to a client.
 *
 * Reports the bound that actually broke rather than the first one checked, so an operator reading
 * the response is not sent to the wrong limit.
 */
private fun refusalMessage(document: Document, limits: SecurityLimits): String {
    return when {
        isIntrospectionOnly(document) -> INTROSPECTION_REFUSED

        estimateCost(document, limits) > limits.maxQueryComplexity ->
            "Query refused: its estimated cost exceeds the maximum of ${limits.maxQueryComplexity}"

        else -> "Query refused: its nesting depth exceeds the maximum of ${limits.maxQueryDepth}"
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
