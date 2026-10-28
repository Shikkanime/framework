package fr.shikkanime.ktor.session

import fr.shikkanime.ktor.ApiResponse
import fr.shikkanime.ktor.ApiResponses
import fr.shikkanime.ktor.GetMapping
import fr.shikkanime.ktor.HttpHeader
import fr.shikkanime.ktor.IController
import fr.shikkanime.ktor.Operation
import fr.shikkanime.ktor.PathParam
import fr.shikkanime.ktor.QueryParam
import fr.shikkanime.ktor.ResponseEntity
import fr.shikkanime.ktor.RestController
import fr.shikkanime.ktor.dtos.ErrorMessageDto
import fr.shikkanime.ktor.dtos.MessageDto
import io.ktor.http.HttpStatusCode

private const val MAX_EVENT_LIMIT = 1_000
private const val MAX_SESSION_LIMIT = 200

/**
 * Read API of the session journals.
 *
 * The controller is registered only when a token is configured; every endpoint requires
 * `Authorization: Bearer <token>`.
 *
 * @param store journal read by the endpoints.
 * @param token bearer token accepted by the endpoints.
 */
@RestController("/v1/session-traces")
class SessionTraceController(
    private val store: SessionStore,
    private val token: String
) : IController {
    /**
     * Returns the timeline of one session.
     *
     * @param sessionId session to read.
     * @param limit maximum number of events returned, from the end of the journal.
     * @param authorization bearer token.
     * @return the session timeline, or an error payload.
     */
    @Suppress("unused") // Bound reflectively by ControllerBinder.
    @GetMapping("/{sessionId}")
    @Operation(
        summary = "Read a session trace",
        description = "Returns the most recent events recorded for one session"
    )
    @ApiResponses([
        ApiResponse(status = 200, description = "Session found", responseType = SessionTrace::class),
        ApiResponse(status = 400, description = "Invalid session id", responseType = ErrorMessageDto::class),
        ApiResponse(
            status = 401,
            description = "Missing or invalid token",
            responseType = ErrorMessageDto::class
        ),
        ApiResponse(status = 404, description = "Session not found", responseType = ErrorMessageDto::class)
    ])
    suspend fun trace(
        @PathParam("sessionId") sessionId: String,
        @QueryParam("limit", required = false) limit: Int?,
        @HttpHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<Any> {
        if (!SessionTokenVerifier.matches(token, authorization)) return unauthorized()
        if (!SessionIds.isValid(sessionId)) return badRequest("Invalid session id")
        val trace = store.find(sessionId, resolveEventLimit(limit)) ?: return notFound()
        return ResponseEntity.ok(trace)
    }

    /**
     * Lists the most recently active sessions.
     *
     * @param limit maximum number of sessions returned.
     * @param authorization bearer token.
     * @return session summaries, or an error payload.
     */
    @Suppress("unused") // Bound reflectively by ControllerBinder.
    @GetMapping
    @Operation(
        summary = "List session traces",
        description = "Returns the sessions with the most recent activity"
    )
    @ApiResponses([
        ApiResponse(
            status = 200,
            description = "Sessions found",
            responseType = List::class,
            typeArguments = [SessionSummary::class]
        ),
        ApiResponse(
            status = 401,
            description = "Missing or invalid token",
            responseType = ErrorMessageDto::class
        )
    ])
    suspend fun sessions(
        @QueryParam("limit", required = false) limit: Int?,
        @HttpHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<Any> {
        if (!SessionTokenVerifier.matches(token, authorization)) return unauthorized()
        return ResponseEntity.ok(store.sessions(resolveSessionLimit(limit)))
    }

    private fun resolveEventLimit(limit: Int?): Int =
        limit?.coerceIn(1, MAX_EVENT_LIMIT) ?: SessionStore.DEFAULT_EVENT_LIMIT

    private fun resolveSessionLimit(limit: Int?): Int =
        limit?.coerceIn(1, MAX_SESSION_LIMIT) ?: SessionStore.DEFAULT_SESSION_LIMIT

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity(MessageDto.error("Unauthorized"), HttpStatusCode.Unauthorized)

    private fun badRequest(message: String): ResponseEntity<Any> =
        ResponseEntity(MessageDto.error(message), HttpStatusCode.BadRequest)

    private fun notFound(): ResponseEntity<Any> =
        ResponseEntity(MessageDto.error("Session not found"), HttpStatusCode.NotFound)
}
