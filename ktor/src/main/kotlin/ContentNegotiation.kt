package fr.shikkanime.ktor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Standard [Json] instance used for framework content negotiation.
 *
 * Configured with `encodeDefaults = true`, `ignoreUnknownKeys = true`, `isLenient = true`, and `explicitNulls = false`.
 */
val defaultJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/**
 * Standard [ProtoBuf] instance used for framework content negotiation.
 *
 * Configured with `encodeDefaults = true`.
 */
@OptIn(ExperimentalSerializationApi::class)
val defaultProtoBuf: ProtoBuf = ProtoBuf {
    encodeDefaults = true
}

/**
 * Standard [Cbor] instance used for framework content negotiation.
 *
 * Configured with `encodeDefaults = true` and `ignoreUnknownKeys = true`.
 */
@OptIn(ExperimentalSerializationApi::class)
val defaultCbor: Cbor = Cbor {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
