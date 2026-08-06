package fr.shikkanime.cache

import kotlin.time.Duration.Companion.days

/**
 * Sample demonstration showing basic usage of [Cache] with [ValkeyWrapperImpl].
 */
suspend fun main() {
    val valkeyWrapper = ValkeyWrapperImpl.connect()
    val cache = Cache(valkeyWrapper = valkeyWrapper)

    val response = cache.get(
        bucket = "episodes",
        key = "1",
        ttl = 2.days
    ) {
        "Hello, World!"
    }

    println(response)
}