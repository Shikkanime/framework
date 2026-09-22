package fr.shikkanime.ktor.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class JwtTokenServiceTest {
    private lateinit var service: JwtTokenService
    private lateinit var otherKeyService: JwtTokenService

    @BeforeEach
    fun setUp() {
        service = JwtTokenService(TestJwtConfig.config())
        otherKeyService = JwtTokenService(TestJwtConfig.otherConfig())
    }

    @Nested
    @DisplayName("Given a freshly issued access token")
    inner class GivenAccessToken {
        @Test
        fun `should carry the subject, roles and token type claims`() {
            // Given
            val identity = JwtIdentity(
                uuid = "user-1",
                roles = setOf("ADMIN", "MEMBER"),
                custom = mapOf("isPrivate" to "true")
            )

            // When
            val verified = service.verifyAccessToken(service.createAccessToken(identity))

            // Then
            assertNotNull(verified)
            assertEquals("user-1", verified!!.subject)
            assertEquals(setOf("ADMIN", "MEMBER"), verified.roles)
            assertEquals(JwtConfig.TOKEN_TYPE_ACCESS, verified.claims[JwtConfig.CLAIM_TOKEN_TYPE])
            assertEquals("true", verified.claims["isPrivate"])
            assertTrue(verified.expiresAt > System.currentTimeMillis())
        }

        @Test
        fun `should carry a unique jti per token`() {
            // Given
            val identity = JwtIdentity(uuid = "user-1")

            // When
            val first = service.verifyAccessToken(service.createAccessToken(identity))!!
            val second = service.verifyAccessToken(service.createAccessToken(identity))!!

            // Then
            assertTrue(first.claims["jti"] != second.claims["jti"])
        }

        @Test
        fun `should respect the configured access ttl`() {
            // Given
            val identity = JwtIdentity(uuid = "user-1")

            // When
            val verified = service.verifyAccessToken(service.createAccessToken(identity))!!

            // Then
            val expectedWindow = TestJwtConfig.config().accessTtl.inWholeMilliseconds
            assertTrue(verified.expiresAt - System.currentTimeMillis() <= expectedWindow)
        }
    }

    @Nested
    @DisplayName("Given tampered or hostile tokens")
    inner class GivenHostileTokens {
        @Test
        fun `should reject a token signed with another key`() {
            // Given
            val forged = otherKeyService.createAccessToken(JwtIdentity(uuid = "attacker"))

            // When
            val verified = service.verifyAccessToken(forged)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject an alg-none token`() {
            // Given
            val header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
            val payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"uuid":"user-1","token_type":"access"}""".toByteArray())
            val unsigned = "$header.$payload."

            // When
            val verified = service.verifyAccessToken(unsigned)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject an HS256 token when pinned to RS256`() {
            // Given
            val hmacToken = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, JwtConfig.TOKEN_TYPE_ACCESS)
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256("attacker-controlled-secret"))

            // When
            val verified = service.verifyAccessToken(hmacToken)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject a token with a tampered payload`() {
            // Given
            val token = service.createAccessToken(JwtIdentity(uuid = "user-1", roles = setOf("MEMBER")))
            val parts = token.split(".")
            val forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(parts[1].let { Base64.getUrlDecoder().decode(it) }
                    .decodeToString()
                    .replace("MEMBER", "ADMIN")
                    .toByteArray())

            // When
            val verified = service.verifyAccessToken("${parts[0]}.$forgedPayload.${parts[2]}")

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject a token with a mismatched audience`() {
            // Given
            val foreignToken = JWT.create()
                .withAudience("other-audience")
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, JwtConfig.TOKEN_TYPE_ACCESS)
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(TestJwtConfig.config().algorithm)

            // When
            val verified = service.verifyAccessToken(foreignToken)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject a token with a mismatched issuer`() {
            // Given
            val foreignToken = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer("https://evil.example.com/")
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, JwtConfig.TOKEN_TYPE_ACCESS)
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(TestJwtConfig.config().algorithm)

            // When
            val verified = service.verifyAccessToken(foreignToken)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject an expired token`() {
            // Given
            val expired = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, JwtConfig.TOKEN_TYPE_ACCESS)
                .withExpiresAt(Date(System.currentTimeMillis() - 1_000))
                .sign(TestJwtConfig.config().algorithm)

            // When
            val verified = service.verifyAccessToken(expired)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject a token without the uuid claim`() {
            // Given
            val anonymous = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, JwtConfig.TOKEN_TYPE_ACCESS)
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(TestJwtConfig.config().algorithm)

            // When
            val verified = service.verifyAccessToken(anonymous)

            // Then
            assertNull(verified)
        }
    }

    @Nested
    @DisplayName("Given cross-token-type confusion attempts")
    inner class GivenTokenTypeConfusion {
        @Test
        fun `should reject a refresh token presented as an access token`() {
            // Given
            val refreshToken = service.createRefreshToken(JwtIdentity(uuid = "user-1"))

            // When
            val verified = service.verifyAccessToken(refreshToken)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject an access token presented as a refresh token`() {
            // Given
            val accessToken = service.createAccessToken(JwtIdentity(uuid = "user-1"))

            // When
            val verified = service.verifyRefreshToken(accessToken)

            // Then
            assertNull(verified)
        }

        @Test
        fun `should reject a token with no token type claim on both paths`() {
            // Given
            val untyped = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(TestJwtConfig.config().algorithm)

            // When
            val asAccess = service.verifyAccessToken(untyped)
            val asRefresh = service.verifyRefreshToken(untyped)

            // Then
            assertNull(asAccess)
            assertNull(asRefresh)
        }
    }

    @Nested
    @DisplayName("Given hostile token construction attempts")
    inner class GivenHostileConstruction {
        @Test
        fun `should reject custom claims colliding with reserved claims`() {
            // Given
            val hostile = JwtIdentity(uuid = "user-1", custom = mapOf(JwtConfig.CLAIM_TOKEN_TYPE to "refresh"))

            // When / Then
            val failure = runCatching { service.createAccessToken(hostile) }
            assertTrue(failure.isFailure)
        }

        @Test
        fun `should reject a blank subject uuid`() {
            // Given
            val anonymous = JwtIdentity(uuid = "")

            // When / Then
            val failure = runCatching { service.createAccessToken(anonymous) }
            assertTrue(failure.isFailure)
        }

        @Test
        fun `should reject the unsecured none algorithm at configuration time`() {
            // Given / When / Then
            val failure = runCatching {
                JwtConfig(algorithm = Algorithm.none(), issuer = TestJwtConfig.ISSUER, audience = TestJwtConfig.AUDIENCE)
            }

            assertTrue(failure.isFailure)
        }

        @Test
        fun `should reject a token without an exp claim`() {
            // Given
            val eternal = JWT.create()
                .withAudience(TestJwtConfig.AUDIENCE)
                .withIssuer(TestJwtConfig.ISSUER)
                .withClaim(JwtConfig.CLAIM_UUID, "user-1")
                .withClaim(JwtConfig.CLAIM_TOKEN_TYPE, "access")
                .withClaim("jti", UUID.randomUUID().toString())
                .sign(TestJwtConfig.config().algorithm)

            // When
            val verified = service.verifyAccessToken(eternal)

            // Then
            assertNull(verified)
        }
    }

    @Nested
    @DisplayName("Given roles encoded as array or single value")
    inner class GivenRolesEncoding {
        @Test
        fun `should round-trip multiple roles`() {
            // Given
            val token = service.createAccessToken(JwtIdentity(uuid = "u", roles = setOf("A", "B", "C")))

            // When
            val verified = service.verifyAccessToken(token)

            // Then
            assertEquals(setOf("A", "B", "C"), verified!!.roles)
        }

        @Test
        fun `should round-trip a single role`() {
            // Given
            val token = service.createAccessToken(JwtIdentity(uuid = "u", roles = setOf("ADMIN")))

            // When
            val verified = service.verifyAccessToken(token)

            // Then
            assertEquals(setOf("ADMIN"), verified!!.roles)
        }

        @Test
        fun `should default to an empty role set`() {
            // Given
            val token = service.createAccessToken(JwtIdentity(uuid = "u"))

            // When
            val verified = service.verifyAccessToken(token)

            // Then
            assertEquals(emptySet<String>(), verified!!.roles)
        }
    }

}

/**
 * Shared JWT configuration for tests.
 */
internal object TestJwtConfig {
    const val ISSUER = "https://test.shikkanime.fr/"
    const val AUDIENCE = "test-audience"

    /**
     * Builds the primary configuration with a dedicated RSA key pair.
     *
     * @return pinned configuration.
     */
    fun config(): JwtConfig {
        val keys = generateKeys()
        return JwtConfig(
            algorithm = Algorithm.RSA256(keys.first, keys.second),
            issuer = ISSUER,
            audience = AUDIENCE,
            realm = "test",
            accessTtl = 15.minutes
        )
    }

    /**
     * Builds a configuration whose access tokens are ALREADY expired at issuance.
     *
     * @return configuration with a negative access TTL.
     */
    fun expiredConfig(): JwtConfig {
        val keys = generateKeys()
        return JwtConfig(
            algorithm = Algorithm.RSA256(keys.first, keys.second),
            issuer = ISSUER,
            audience = AUDIENCE,
            realm = "test",
            accessTtl = -15.minutes
        )
    }

    /**
     * Builds a configuration pinned to a DIFFERENT key pair, simulating a foreign issuer.
     *
     * @return configuration with unrelated keys.
     */
    fun otherConfig(): JwtConfig {
        val keys = generateKeys()
        return JwtConfig(
            algorithm = Algorithm.RSA256(keys.first, keys.second),
            issuer = ISSUER,
            audience = AUDIENCE,
            realm = "test",
            accessTtl = 15.minutes
        )
    }

    /**
     * Generates a fresh RSA-2048 key pair.
     *
     * @return public/private key pair.
     */
    private fun generateKeys(): Pair<RSAPublicKey, RSAPrivateKey> {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        return pair.public as RSAPublicKey to pair.private as RSAPrivateKey
    }
}
