package fr.shikkanime.ktor.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Client-side JWT helper adding the Bearer header and refreshing expired access tokens.
 *
 * On an HTTP 401, the interceptor exchanges the stored refresh token once for a new pair via
 * [refreshEndpoint] and replays the request exactly one time; a failed rotation propagates the
 * original 401. The refresh call itself never carries the interceptor, so no loop is possible.
 *
 * @property httpClient client used for the refresh exchange.
 * @property refreshEndpoint token rotation URL accepting a JSON `{"token": "..."}` body and
 * answering a JSON [TokenPairResponse].
 * @property tokenStorage mutable holder of the current token pair, owned by the consumer.
 */
class JwtClientInterceptor(
    private val httpClient: HttpClient,
    private val refreshEndpoint: String,
    private val tokenStorage: JwtTokenStorage
)

/**
 * Mutable holder of the current token pair.
 *
 * Kept minimal so each consumer maps it to its own persistence (memory, keychain, encrypted
 * storage, ...).
 */
interface JwtTokenStorage {
    /**
     * @return the current token pair, or `null` before the first login.
     */
    fun current(): TokenPair?

    /**
     * Replaces the current token pair, typically after a rotation.
     *
     * @param pair fresh pair to persist.
     */
    fun update(pair: TokenPair)

    /**
     * @return the raw refresh token, or `null` when none is stored.
     */
    fun refreshToken(): String?
}

/**
 * Wire representation of a rotation response.
 *
 * @property accessToken fresh access token.
 * @property refreshToken fresh single-use refresh token.
 */
data class TokenPairResponse(val accessToken: String, val refreshToken: String)
