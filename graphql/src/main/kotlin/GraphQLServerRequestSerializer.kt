package fr.shikkanime.graphql

import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * Converts a JSON object to a plain value map, ready for the GraphQL execution engine.
 *
 * @receiver the JSON object to convert.
 * @return the same entries with plain Kotlin values.
 */
private fun JsonObject.toValueMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key to value.toValue() }

/**
 * Converts a JSON element to a plain Kotlin value.
 *
 * @receiver the JSON element to convert.
 * @return `null` for JSON null, the content for a primitive, and a recursive plain value for
 * objects and arrays.
 */
private fun JsonElement.toValue(): Any? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive -> if (isString) contentOrNull else booleanOrNull ?: contentOrNull
        is JsonObject -> toValueMap()
        is JsonArray -> map(JsonElement::toValue)
    }

/**
 * Converts one request object from its JSON form.
 *
 * @receiver the JSON object of a single GraphQL request.
 * @return the parsed request.
 * @throws SerializationException when the `query` field is missing.
 */
private fun JsonObject.toRequest(): GraphQLRequest {
    val query = this["query"]?.jsonPrimitive?.contentOrNull
        ?: throw SerializationException("A GraphQL request requires a 'query' field")

    return GraphQLRequest(
        query = query,
        variables = (this["variables"] as? JsonObject)?.toValueMap(),
        operationName = this["operationName"]?.jsonPrimitive?.contentOrNull,
        extensions = (this["extensions"] as? JsonObject)?.toValueMap()
    )
}

/**
 * Converts one request to its JSON object form.
 *
 * @receiver the request to write.
 * @return the JSON object understood by the GraphQL endpoint.
 */
private fun GraphQLRequest.toElement(): JsonObject =
    JsonObject(
        buildMap {
            put("query", JsonPrimitive(query))
            variables?.let { put("variables", it.toValueElement()) }
            operationName?.let { put("operationName", JsonPrimitive(it)) }
            extensions?.let { put("extensions", it.toValueElement()) }
        }
    )

/**
 * Converts a plain value map back to its JSON object form.
 *
 * @receiver the values to write.
 * @return the JSON object carrying them.
 */
private fun Map<String, Any?>.toValueElement(): JsonObject =
    JsonObject(mapValues { (_, value) -> value.toElementValue() })

/**
 * Converts a plain Kotlin value back to its JSON element form.
 *
 * @receiver the value to write, `null` included.
 * @return the JSON element carrying it.
 */
private fun Any?.toElementValue(): JsonElement =
    when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toElementValue() })
        is List<*> -> JsonArray(this.map(Any?::toElementValue))
        else -> JsonPrimitive(this.toString())
    }

/**
 * Serialization module carrying [GraphQLServerRequest] and [GraphQLResponse].
 *
 * graphql-kotlin ships these types with Jackson and FastJson deserializers only, so a kotlinx
 * ContentNegotiation can neither read a GraphQL body nor write its response: every request dies
 * with "Cannot transform this request's content". Registering this module in the application JSON
 * (see `configureDefaultModules`) makes the GraphQL endpoint consumable like any other route.
 */
val graphQLSerializersModule: SerializersModule =
    SerializersModule {
        contextual(GraphQLServerRequest::class, GraphQLServerRequestSerializer)
        contextual(GraphQLResponse::class, GraphQLResponseSerializer)
    }

/**
 * Reads and writes [GraphQLServerRequest] as its wire form.
 *
 * A single request is a JSON object, a batch is a JSON array of them - exactly what the GraphQL
 * over HTTP specification describes and what the endpoint already expects.
 */
object GraphQLServerRequestSerializer : KSerializer<GraphQLServerRequest> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("GraphQLServerRequest")

    /**
     * Parses one request object or a batch of them.
     *
     * @param decoder JSON decoder of the request body.
     * @return the decoded request.
     * @throws SerializationException when the body is neither an object nor an array of objects.
     */
    override fun deserialize(decoder: Decoder): GraphQLServerRequest {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())

        return when (element) {
            is JsonArray -> GraphQLBatchRequest(element.map { it.jsonObject.toRequest() })
            is JsonObject -> element.toRequest()
            else -> throw SerializationException("A GraphQL request is a JSON object or a batch array")
        }
    }

    /**
     * Writes the request back to its wire form.
     *
     * @param encoder JSON encoder of the request body.
     * @param value request to write.
     */
    override fun serialize(encoder: Encoder, value: GraphQLServerRequest) {
        val element = when (value) {
            is GraphQLBatchRequest -> JsonArray(value.requests.map(GraphQLRequest::toElement))
            else -> (value as GraphQLRequest).toElement()
        }

        encoder.encodeSerializableValue(JsonElement.serializer(), element)
    }
}

/**
 * Writes [GraphQLResponse] to its wire form.
 *
 * The response type is as Jackson-only as the request one, so the answer of every GraphQL call is
 * a serialization failure without this encoder. Only the encoding direction matters: the endpoint
 * never reads a response body.
 */
object GraphQLResponseSerializer : KSerializer<GraphQLResponse<*>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("GraphQLResponse")

    /**
     * Parses a response body - the endpoint never does.
     *
     * @param decoder JSON decoder of the body.
     * @throws SerializationException always: responses are written, never read.
     */
    override fun deserialize(decoder: Decoder): GraphQLResponse<*> =
        throw SerializationException("A GraphQL response is written, never read")

    /**
     * Writes the response as `data` / `errors` / `extensions`.
     *
     * @param encoder JSON encoder of the response body.
     * @param value response to write.
     */
    override fun serialize(encoder: Encoder, value: GraphQLResponse<*>) {
        encoder.encodeSerializableValue(JsonElement.serializer(), value.toElement())
    }

    /**
     * Converts a response to its JSON object form.
     *
     * @receiver the response to write.
     * @return the JSON object carrying `data`, `errors` and `extensions`.
     */
    private fun GraphQLResponse<*>.toElement(): JsonObject =
        JsonObject(
            buildMap {
                data?.let { put("data", it.toElementValue()) }
                errors?.let { errors ->
                    put(
                        "errors",
                        JsonArray(
                            errors.map { error ->
                                JsonObject(
                                    buildMap {
                                        put("message", JsonPrimitive(error.message))
                                        error.path?.let { path ->
                                            put("path", JsonArray(path.map { step -> JsonPrimitive(step.toString()) }))
                                        }
                                        error.extensions?.let { put("extensions", it.toElementValue()) }
                                    }
                                )
                            }
                        )
                    )
                }
                extensions?.let { put("extensions", it.toElementValue()) }
            }
        )
}
