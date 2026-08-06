package fr.shikkanime

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import java.nio.ByteBuffer

@OptIn(ExperimentalSerializationApi::class)
class BinaryCodec(
    private val format: BinaryFormat = Cbor
) {
    fun <T> encode(value: T, serializer: KSerializer<T>): ByteArray =
        format.encodeToByteArray(serializer, value)

    fun <T> decode(bytes: ByteArray, serializer: KSerializer<T>): T =
        format.decodeFromByteArray(serializer, bytes)

    fun encodeVersion(value: Long): ByteArray =
        ByteBuffer.allocate(Long.SIZE_BYTES)
            .putLong(value)
            .array()

    fun decodeVersion(bytes: ByteArray): Long =
        ByteBuffer.wrap(bytes).long
}