package fr.shikkanime.ktor

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Configures the default content negotiation and CORS modules, and registers the built-in [HealthController].
 *
 * Content negotiation supports JSON, Protobuf, and CBOR formats using standard framework configurations,
 * while CORS accepts requests from any host using Ktor's default HTTP methods. A `GET /health` route
 * returning `OK` is bound automatically so every consumer exposes the same UP endpoint.
 */
fun Application.configureDefaultModules(vararg extraModules: SerializersModule = emptyArray()) {
    install(ResponseSizeRecorder)
    configureContentNegotiation(*extraModules)
    configureCORS()
    routing {
        ControllerBinder.register(this, listOf(HealthController()))
    }
}

/**
 * Builds the JSON converter of the content negotiation, with [extraModules] merged in.
 *
 * @param extraModules kotlinx serialization modules, one per wire type the framework cannot
 * annotate itself (the GraphQL request types, for example).
 * @return the JSON format every JSON route serializes with.
 */
private fun buildJsonConverter(extraModules: Array<out SerializersModule>): Json =
    if (extraModules.isEmpty())
        defaultJson
    else
        Json(defaultJson) {
            serializersModule = extraModules.fold(defaultJson.serializersModule) { acc, module ->
                SerializersModule {
                    include(acc)
                    include(module)
                }
            }
        }

/**
 * Configures Content Negotiation on the server using [defaultJson], [defaultProtoBuf], and [defaultCbor].
 *
 * @param extraModules kotlinx serialization modules merged into the JSON converter, one per wire
 * type the framework cannot annotate itself (the GraphQL request types, for example).
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.configureContentNegotiation(vararg extraModules: SerializersModule = emptyArray()) {
    install(ContentNegotiation) {
        json(buildJsonConverter(extraModules))
        // GraphQL clients send the body as text/plain, octet-stream, or with no Content-Type at
        // all: the JSON converter is the fallback for whatever the request announces.
        json(buildJsonConverter(extraModules), ContentType.Any)
        protobuf(defaultProtoBuf)
        cbor(defaultCbor)
    }
}

/**
 * Configures CORS on the server to accept requests from any host using default HTTP methods.
 */
internal fun Application.configureCORS() {
    install(CORS) {
        anyHost()
        HttpMethod.DefaultMethods.forEach(::allowMethod)
    }
}

/**
 * Adds a Swagger UI endpoint at `/swagger` for the routes registered under this route.
 *
 * @param title title displayed in the generated OpenAPI documentation.
 * @param version API version displayed in the generated OpenAPI documentation.
 */
fun Route.configureSwaggerRoute(title: String, version: String) {
    swaggerUI("/swagger") {
        info = OpenApiInfo(title, version)
        source = OpenApiDocSource.Routing(ContentType.Application.Json) {
            routingRoot.descendants()
        }
    }
}