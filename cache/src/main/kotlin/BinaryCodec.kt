package fr.shikkanime.cache

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import java.nio.ByteBuffer

/**
 * A binary serialization and deserialization codec for framework caching.
 *
 * Encodes and decodes objects using Kotlinx Serialization [BinaryFormat] (defaulting to [Cbor])
 * and converts numeric versions to byte representations via [ByteBuffer].
 *
 * @param format The binary serialization format used for encoding and decoding payloads.
 */
@OptIn(ExperimentalSerializationApi::class)
class BinaryCodec(
    private val format: BinaryFormat = Cbor
) {
    /**
     * Encodes a value into a byte array using the provided [KSerializer].
     *
     * @param T The type of object to encode.
     * @param value The instance to serialize.
     * @param serializer The Kotlinx serialization strategy.
     * @return The serialized byte array representation.
     */
    fun <T> encode(value: T, serializer: KSerializer<T>): ByteArray =
        format.encodeToByteArray(serializer, value)

    /**
     * Decodes a byte array into an object of type [T] using the provided [KSerializer].
     *
     * @param T The type of object to decode.
     * @param bytes The raw byte array payload.
     * @param serializer The Kotlinx deserialization strategy.
     * @return The deserialized instance of [T].
     */
    fun <T> decode(bytes: ByteArray, serializer: KSerializer<T>): T =
        format.decodeFromByteArray(serializer, bytes)

    /**
     * Encodes a [Long] version number into an 8-byte array.
     *
     * @param value The numeric version value to encode.
     * @return An 8-byte array representing the version.
     */
    fun encodeVersion(value: Long): ByteArray =
        ByteBuffer.allocate(Long.SIZE_BYTES)
            .putLong(value)
            .array()

    /**
     * Decodes an 8-byte array into a [Long] version number.
     *
     * @param bytes The 8-byte array containing the encoded version.
     * @return The decoded version number as a [Long].
     */
    fun decodeVersion(bytes: ByteArray): Long =
        ByteBuffer.wrap(bytes).long
}