package fr.shikkanime.ktor.session

import io.ktor.util.AttributeKey

/**
 * Call attribute holding the [SessionScope] resolved for the request.
 */
internal val SessionScopeAttribute: AttributeKey<SessionScope> = AttributeKey("SessionScope")

/**
 * Call attribute holding the [System.nanoTime] at which the request started.
 */
internal val SessionStartAttribute: AttributeKey<Long> = AttributeKey("SessionStart")
