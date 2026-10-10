package fr.shikkanime.ktor.auth

import fr.shikkanime.ktor.ControllerBinder
import fr.shikkanime.ktor.GetMapping
import fr.shikkanime.ktor.IController
import fr.shikkanime.ktor.PostMapping
import fr.shikkanime.ktor.RestController
import fr.shikkanime.ktor.configureDefaultModules
import fr.shikkanime.ktor.dtos.MessageDto
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

/**
 * End-to-end server tests: JWT-protected routes bound through [ControllerBinder].
 */
class JwtServerIntegrationTest {
    @Serializable
    data class WhoAmI(val uuid: String, val roles: List<String>)

    @RestController("/api/secure")
    class SecureController : IController {
        @JwtAuthenticated
        @GetMapping("/me")
        fun me(@JwtUser uuid: Uuid): WhoAmI =
            WhoAmI(uuid.toString(), emptyList())

        @JwtRoles("ADMIN")
        @GetMapping("/admin-only")
        fun adminOnly(): WhoAmI =
            WhoAmI("internal", listOf("ADMIN"))

        @JwtAuthenticated(optional = true)
        @GetMapping("/maybe")
        fun maybe(@JwtUser uuid: Uuid?): String =
            uuid?.toString() ?: "anonymous"

        @PostMapping("/refresh")
        fun refresh(@JwtUser uuid: Uuid): WhoAmI =
            WhoAmI(uuid.toString(), emptyList())
    }

    private lateinit var tokenService: JwtTokenService
    private lateinit var rotationService: RefreshTokenService

    @BeforeEach
    fun setUp() {
        resetJwtProviderFlag()
        tokenService = JwtTokenService(TestJwtConfig.config())
        rotationService = RefreshTokenService(tokenService, InMemoryRefreshTokenStore())
    }

    /**
     * Builds a test application with JWT configured and the controller bound.
     *
     * @param block additional request configuration.
     * @return the test application builder.
     */
    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureDefaultModules()
            configureJwtAuth(tokenService)
            routing {
                ControllerBinder.register(this, listOf(SecureController()))
            }
        }
        block()
    }

    /**
     * Issues a valid access token for a subject.
     *
     * @param uuid subject identifier.
     * @param roles subject roles.
     * @return compact access token.
     */
    private fun accessToken(uuid: String, roles: Set<String> = setOf("MEMBER")): String =
        tokenService.createAccessToken(JwtIdentity(uuid = uuid, roles = roles))

    @Nested
    @DisplayName("Given a protected route")
    inner class GivenProtectedRoute {
        @Test
        suspend fun `should accept a valid access token and resolve the jwt user`() = app {
            val response = client.get("/api/secure/me") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken(TestUuids.USER_1)}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains(TestUuids.USER_1))
        }

        @Test
        suspend fun `should return 403 when the principal lacks the required role`() = app {
            val response = client.get("/api/secure/admin-only") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken(TestUuids.USER_1, roles = setOf("MEMBER"))}")
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

        @Test
        suspend fun `should allow access when the principal carries the required role`() = app {
            val response = client.get("/api/secure/admin-only") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken(TestUuids.USER_1, roles = setOf("ADMIN"))}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        suspend fun `should reject an anonymous request with a sanitized 401`() = app {
            val response = client.get("/api/secure/me")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("not authorized"))
        }

        @Test
        suspend fun `should answer identically for expired, tampered and malformed tokens`() = app {
            val tampered = client.get("/api/secure/me") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken(TestUuids.USER_1).dropLast(4)}AAAA")
            }
            val malformed = client.get("/api/secure/me") {
                header(HttpHeaders.Authorization, "Bearer not-a-jwt")
            }
            val foreign = client.get("/api/secure/me") {
                header(HttpHeaders.Authorization, "Bearer ${JwtTokenService(TestJwtConfig.otherConfig()).createAccessToken(JwtIdentity(uuid = TestUuids.USER_1))}")
            }

            assertEquals(HttpStatusCode.Unauthorized, tampered.status)
            assertEquals(HttpStatusCode.Unauthorized, malformed.status)
            assertEquals(HttpStatusCode.Unauthorized, foreign.status)
            assertEquals(tampered.bodyAsText(), malformed.bodyAsText())
            assertEquals(tampered.bodyAsText(), foreign.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Given optional authentication")
    inner class GivenOptionalAuthentication {
        @Test
        suspend fun `should bind null to a nullable jwt user without a token`() = app {
            val response = client.get("/api/secure/maybe")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("anonymous", response.bodyAsText())
        }

        @Test
        suspend fun `should bind the subject when a token is present`() = app {
            val response = client.get("/api/secure/maybe") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken(TestUuids.USER_9)}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(TestUuids.USER_9, response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Given the fail-fast binding contract")
    inner class GivenFailFast {
        @Test
        suspend fun `should reject binding when no jwt provider is configured`() {
            // Given / When
            val failure = runCatching {
                testApplication {
                    application {
                        configureDefaultModules()
                        routing { ControllerBinder.register(this, listOf(SecureController())) }
                    }
                }
            }

            // Then
            assertTrue(failure.isFailure)
            assertTrue(failure.exceptionOrNull()!!.message!!.contains("configureJwtAuth"))
        }
    }

    @Nested
    @DisplayName("Given a full login and rotation flow")
    inner class GivenFullFlow {
        @Test
        suspend fun `should rotate a refresh token through the registered store`() = app {
            // Given
            val refresh = tokenService.createRefreshToken(JwtIdentity(uuid = TestUuids.USER_1))
            rotationService.register(refresh, userId = TestUuids.USER_1)

            // When
            val pair = rotationService.rotate(refresh)!!
            val response = client.get("/api/secure/me") {
                header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
            }

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains(TestUuids.USER_1))
        }
    }

}


/**
 * Valid UUID subjects used across integration tests.
 */
internal object TestUuids {
    val USER_1 = Uuid.random().toString()
    val USER_9 = Uuid.random().toString()
}
