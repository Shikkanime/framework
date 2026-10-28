package fr.shikkanime.ktor

import fr.shikkanime.ktor.session.FileSessionStore
import fr.shikkanime.ktor.session.SessionConfig
import fr.shikkanime.ktor.session.SessionHeaders
import fr.shikkanime.ktor.session.SessionTraceController
import fr.shikkanime.ktor.session.installSessionTracking
import fr.shikkanime.ktor.session.resolvedDirectory
import fr.shikkanime.ktor.session.resolvedService
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
 * Configures the default content negotiation, CORS, session tracking, and the built-in
 * [HealthController].
 *
 * Content negotiation supports JSON, Protobuf, and CBOR formats using standard framework
 * configurations, while CORS accepts requests from any host using Ktor's default HTTP methods and
 * exposes the session headers to browser clients. A `GET /health` route returning `OK` is bound
 * automatically so every consumer exposes the same UP endpoint.
 *
 * When [session] is enabled, every request is journaled under
 * `logs/sessions/<service>/<sessionId>.jsonl`, and the read API is registered when a token is set.
 *
 * @param extraModules kotlinx serialization modules merged into the JSON converter.
 * @param session session tracking configuration.
 */
fun Application.configureDefaultModules(
    vararg extraModules: SerializersModule = emptyArray(),
    session: SessionConfig = SessionConfig()
) {
    install(ResponseSizeRecorder)
    configureContentNegotiation(*extraModules)
    configureCORS()

    val store = if (session.enabled) {
        session.store ?: FileSessionStore(
            directory = session.resolvedDirectory(),
            service = session.resolvedService(),
            retention = session.retention
        )
    } else {
        null
    }

    if (store != null) {
        installSessionTracking(session, store, closeStoreOnStop = session.store == null)
    }

    routing {
        val controllers = mutableListOf<Any>(HealthController())
        val token = session.apiToken?.takeIf { it.isNotBlank() }
        if (store != null && token != null) {
            controllers += SessionTraceController(store, token)
        }
        ControllerBinder.register(this, controllers)
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
        // Both registrations are needed, and testing removed each one to prove it:
        // - Application.Json answers the Accept-based response negotiation (a lone Any answers
        //   406 Not Acceptable instead);
        // - Any parses a request body sent as text/plain, octet-stream, or with no Content-Type,
        //   which is how GraphQL clients and .http files post their query.
        json(buildJsonConverter(extraModules))
        json(buildJsonConverter(extraModules), ContentType.Any)
        protobuf(defaultProtoBuf)
        cbor(defaultCbor)
    }
}

/**
 * Configures CORS on the server to accept requests from any host using default HTTP methods.
 *
 * The session correlation headers are allowed in preflight requests and exposed on responses, so a
 * browser client can read the identifiers the server assigned to its session.
 */
internal fun Application.configureCORS() {
    install(CORS) {
        anyHost()
        HttpMethod.DefaultMethods.forEach(::allowMethod)
        allowHeader(SessionHeaders.SESSION_ID)
        allowHeader(SessionHeaders.REQUEST_ID)
        allowHeader(SessionHeaders.PARENT_REQUEST_ID)
        exposeHeader(SessionHeaders.SESSION_ID)
        exposeHeader(SessionHeaders.REQUEST_ID)
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