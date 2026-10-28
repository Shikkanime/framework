package fr.shikkanime.ktor.session

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

private const val BEARER_PREFIX = "Bearer "

/**
 * Verifies the bearer token protecting the session read API.
 */
internal object SessionTokenVerifier {
    /**
     * Compares the configured token with an `Authorization` header in constant time.
     *
     * @param expected configured token.
     * @param authorization value of the `Authorization` header, when present.
     * @return `true` when the header carries the expected bearer token.
     */
    fun matches(expected: String, authorization: String?): Boolean {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) return false
        val actual = authorization.substring(BEARER_PREFIX.length)
        return MessageDigest.isEqual(expected.toByteArray(UTF_8), actual.toByteArray(UTF_8))
    }
}
