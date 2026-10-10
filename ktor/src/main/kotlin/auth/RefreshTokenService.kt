package fr.shikkanime.ktor.auth

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.uuid.Uuid

/**
 * Server-side record of one issued refresh token.
 *
 * The store only ever sees [tokenHash] — the SHA-256 hex digest of the raw token — so a leak of
 * the store never yields usable credentials.
 *
 * @property tokenHash SHA-256 hex digest of the raw refresh token.
 * @property familyId rotation lineage shared by every token minted from one login.
 * @property userId subject the token was issued to.
 * @property expiresAt expiration instant in epoch milliseconds.
 * @property usedAt consumption instant in epoch milliseconds, or `null` while unused.
 */
data class RefreshTokenRecord(
    val tokenHash: String,
    val familyId: String,
    val userId: String,
    val expiresAt: Long,
    val usedAt: Long? = null
)

/**
 * Persistence SPI for refresh token rotation.
 *
 * The framework never assumes a storage technology: implement this interface over the database or
 * cache of your choice (an [InMemoryRefreshTokenStore] is provided for tests and simple
 * deployments). Implementations MUST make [consume] atomic — two concurrent presentations of the
 * same token must yield exactly one [RefreshTokenRecord.usedAt] transition — because the
 * reuse-detection guarantee of RFC 9700 §4.14.2 rests on it.
 */
interface RefreshTokenStore {
    /**
     * Persists a freshly issued refresh token record.
     *
     * @param token record to persist; [RefreshTokenRecord.tokenHash] is the lookup key.
     */
    fun store(token: RefreshTokenRecord)

    /**
     * Atomically consumes the token identified by [tokenHash].
     *
     * @param tokenHash SHA-256 hex digest of the raw refresh token.
     * @return the record as it was before consumption — `usedAt == null` on first use — the
     * already-consumed record (`usedAt != null`) when the token is replayed, or `null` when the
     * hash is unknown, expired, or belongs to a revoked family.
     */
    fun consume(tokenHash: String): RefreshTokenRecord?

    /**
     * Revokes every token sharing [familyId], used when a replay is detected.
     *
     * @param familyId lineage to invalidate entirely.
     */
    fun revokeFamily(familyId: String)
}

/**
 * Thread-safe in-memory [RefreshTokenStore].
 *
 * Suited to tests and single-instance deployments; the family revocation flag makes replays fail
 * even for records whose own lifecycle is untouched.
 */
class InMemoryRefreshTokenStore : RefreshTokenStore {
    private val records: ConcurrentMap<String, RefreshTokenRecord> = ConcurrentHashMap()
    private val revokedFamilies = ConcurrentHashMap.newKeySet<String>()

    override fun store(token: RefreshTokenRecord) {
        records[token.tokenHash] = token
    }

    override fun consume(tokenHash: String): RefreshTokenRecord? {
        val record = records[tokenHash] ?: return null

        return synchronized(record) {
            val current = records[tokenHash] ?: return null

            if (current.familyId in revokedFamilies)
                return null

            if (current.expiresAt <= System.currentTimeMillis())
                return null

            if (current.usedAt != null) return current

            records[tokenHash] = current.copy(usedAt = System.currentTimeMillis())
            current
        }
    }

    override fun revokeFamily(familyId: String) {
        revokedFamilies.add(familyId)
    }
}

/**
 * Rotates refresh tokens with RFC 9700 §4.14.2 reuse detection.
 *
 * On rotation the verified refresh token is consumed atomically; presenting it again is treated as
 * theft and revokes its entire family. New tokens derive their claims exclusively from the
 * verified token — never from caller-supplied data.
 *
 * @param tokenService JWT machinery used to verify and mint tokens.
 * @param store persistence backing the rotation lineage.
 */
class RefreshTokenService(
    private val tokenService: JwtTokenService,
    private val store: RefreshTokenStore
) {
    /**
     * Exchanges a valid, unused refresh token for a new token pair.
     *
     * @param rawRefreshToken compact refresh token received from the client.
     * @return the new access and refresh tokens, or `null` when the token is invalid, expired,
     * already consumed, or belongs to a revoked family.
     */
    fun rotate(rawRefreshToken: String): TokenPair? {
        val verified = tokenService.verifyRefreshToken(rawRefreshToken) ?: return null
        val tokenHash = hash(rawRefreshToken)
        val record = store.consume(tokenHash) ?: return null

        if (record.usedAt != null) {
            store.revokeFamily(record.familyId)
            return null
        }

        val claims = JwtIdentity(
            uuid = verified.subject,
            roles = verified.roles,
            custom = verified.claims.filterKeys { it !in JwtConfig.RESERVED_CLAIMS }
        )
        val familyId = record.familyId

        val newRefresh = tokenService.createRefreshToken(claims)
        store.store(
            RefreshTokenRecord(
                tokenHash = hash(newRefresh),
                familyId = familyId,
                userId = verified.subject,
                expiresAt = System.currentTimeMillis() + tokenService.config.refreshTtl.inWholeMilliseconds
            )
        )

        return TokenPair(
            accessToken = tokenService.createAccessToken(claims),
            refreshToken = newRefresh
        )
    }

    /**
     * Registers the first refresh token of a lineage after a successful login.
     *
     * @param rawRefreshToken refresh token minted by [JwtTokenService.createRefreshToken].
     * @param userId subject the token belongs to.
     */
    fun register(rawRefreshToken: String, userId: String) {
        store.store(
            RefreshTokenRecord(
                tokenHash = hash(rawRefreshToken),
                familyId = Uuid.random().toString(),
                userId = userId,
                expiresAt = System.currentTimeMillis() + tokenService.config.refreshTtl.inWholeMilliseconds
            )
        )
    }

    /**
     * Hashes a raw token for storage.
     *
     * @param raw compact token.
     * @return SHA-256 hex digest.
     */
    private fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }


}

/**
 * Result of a successful refresh: a fresh access token and its rotated companion.
 *
 * @property accessToken short-lived token for API calls.
 * @property refreshToken single-use replacement for the consumed refresh token.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String
)
