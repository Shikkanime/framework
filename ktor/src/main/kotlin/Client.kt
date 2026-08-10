package fr.shikkanime.ktor

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Creates a preconfigured [HttpClient] using the OkHttp engine.
 *
 * Configures HTTP timeouts (30s request, 10s connect, 30s socket) and registers content negotiation
 * for JSON, Protobuf, and CBOR using [defaultJson], [defaultProtoBuf], and [defaultCbor].
 *
 * @return a new preconfigured [HttpClient] instance.
 */
@OptIn(ExperimentalSerializationApi::class)
fun createHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }

        install(ContentNegotiation) {
            json(defaultJson)
            protobuf(defaultProtoBuf)
            cbor(defaultCbor)
        }
    }