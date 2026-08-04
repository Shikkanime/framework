package fr.shikkanime.ktor

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Configures the default content negotiation and CORS modules.
 *
 * Content negotiation supports JSON and Protobuf, while CORS accepts requests from any host
 * using Ktor's default HTTP methods.
 */
fun Application.configureDefaultModules(): Unit {
    configureContentNegotiation()
    configureCORS()
}

@OptIn(ExperimentalSerializationApi::class)
internal fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json()
        protobuf()
    }
}

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