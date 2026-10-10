package fr.shikkanime.ktor.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RefreshTokenServiceTest {
    private lateinit var tokenService: JwtTokenService
    private lateinit var store: InMemoryRefreshTokenStore
    private lateinit var rotationService: RefreshTokenService

    @BeforeEach
    fun setUp() {
        tokenService = JwtTokenService(TestJwtConfig.config())
        store = InMemoryRefreshTokenStore()
        rotationService = RefreshTokenService(tokenService, store)
    }

    @Nested
    @DisplayName("Given a login issuing a refresh token")
    inner class GivenLogin {
        @Test
        fun `should rotate into a fresh usable pair`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1", roles = setOf("MEMBER")))
            rotationService.register(refresh, userId = "user-1")

            // When
            val pair = rotationService.rotate(refresh)

            // Then
            assertNotNull(pair)
            assertNotNull(tokenService.verifyAccessToken(pair!!.accessToken))
            assertNotEquals(refresh, pair.refreshToken)
            assertNotNull(tokenService.verifyRefreshToken(pair.refreshToken))
        }

        @Test
        fun `should derive rotated claims from the verified token only`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1", roles = setOf("MEMBER")))
            rotationService.register(refresh, userId = "user-1")

            // When
            val pair = rotationService.rotate(refresh)!!
            val verified = tokenService.verifyAccessToken(pair.accessToken)!!

            // Then
            assertEquals("user-1", verified.subject)
            assertEquals(setOf("MEMBER"), verified.roles)
        }

        @Test
        fun `should keep the same family across rotations`() {
            // Given
            val first = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))
            rotationService.register(first, userId = "user-1")
            val firstHash = first.hashCode().toString()
            val firstPair = rotationService.rotate(first)!!

            // When
            rotationService.register(firstPair.refreshToken, userId = "user-1")
            val secondPair = rotationService.rotate(firstPair.refreshToken)

            // Then
            assertNotNull(secondPair)
            assertTrue(firstPair.refreshToken != secondPair!!.refreshToken)
        }
    }

    @Nested
    @DisplayName("Given a replayed refresh token")
    inner class GivenReplay {
        @Test
        fun `should reject an already consumed token and revoke its family`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))
            rotationService.register(refresh, userId = "user-1")
            val firstPair = rotationService.rotate(refresh)!!

            // When
            val replayed = rotationService.rotate(refresh)

            // Then
            assertNull(replayed)
            assertNull(rotationService.rotate(firstPair.refreshToken))
        }

        @Test
        fun `should reject an unknown token hash`() {
            // Given
            val neverRegistered = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))

            // When
            val pair = rotationService.rotate(neverRegistered)

            // Then
            assertNull(pair)
        }

        @Test
        fun `should reject an expired refresh token`() {
            // Given
            val expiredRefresh = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, "refresh")
                .withExpiresAt(Date(System.currentTimeMillis() - 1_000))
                .sign(TestJwtConfig.config().algorithm)
            rotationService.register(expiredRefresh, userId = "user-1")

            // When
            val pair = rotationService.rotate(expiredRefresh)

            // Then
            assertNull(pair)
        }

        @Test
        fun `should treat a family-revoked token as a plain failure`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))
            rotationService.register(refresh, userId = "user-1")
            val familyId = store.consume(hashFor(refresh))?.familyId
            assertNotNull(familyId)
            store.revokeFamily(familyId!!)

            // When
            val pair = rotationService.rotate(refresh)

            // Then
            assertNull(pair)
        }

        @Test
        fun `should serialize concurrent rotations to a single winner`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))
            rotationService.register(refresh, userId = "user-1")
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val results = (1..2).map {
                pool.submit<Boolean> {
                    start.await()
                    rotationService.rotate(refresh) != null
                }
            }

            // When
            start.countDown()
            val winners = results.map { it.get(10, TimeUnit.SECONDS) }.count { it }
            pool.shutdown()

            // Then
            assertEquals(1, winners)
        }
    }

    @Nested
    @DisplayName("Given the store contract")
    inner class GivenStore {
        @Test
        fun `should store only a hash, never the raw token`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))
            rotationService.register(refresh, userId = "user-1")

            // When
            val stored = store.consume(hashFor(refresh))

            // Then
            assertNotNull(stored)
            assertFalse(stored!!.tokenHash.contains(refresh))
        }

        @Test
        fun `should record the consumption instant`() {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = "user-1"))
            rotationService.register(refresh, userId = "user-1")

            // When
            val before = store.consume(hashFor(refresh))!!
            val replayed = store.consume(hashFor(refresh))!!

            // Then
            assertNull(before.usedAt)
            assertNotNull(replayed.usedAt)
        }
    }

    /**
     * Computes the SHA-256 hex hash exactly as [RefreshTokenService] does.
     *
     * @param raw compact token.
     * @return hex digest.
     */
    private fun hashFor(raw: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
