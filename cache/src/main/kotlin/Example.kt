package fr.shikkanime

import kotlin.time.Duration.Companion.days

suspend fun main() {
    val valkeyWrapper = ValkeyWrapperImpl.connect()
    val cache = Cache(valkeyWrapper = valkeyWrapper)

    cache.get(
        bucket = "episodes",
        key = "1",
        ttl = 2.days
    ) {
        "Hello, World!"
    }
}