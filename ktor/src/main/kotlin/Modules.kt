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

fun Application.configureDefaultModules(title: String, version: String) {
    configureContentNegotiation()
    configureCORS()

    routing {
        configureSwaggerRoute(title, version)
    }
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

internal fun Route.configureSwaggerRoute(title: String, version: String) {
    swaggerUI("/swagger") {
        info = OpenApiInfo(title, version)
        source = OpenApiDocSource.Routing(ContentType.Application.Json) {
            routingRoot.descendants()
        }
    }
}