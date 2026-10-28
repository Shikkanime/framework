package fr.shikkanime.ktor.session

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

private const val LOG_DIRECTORY_ENV = "SHIKKANIME_LOGS_DIR"
private const val SERVICE_ENV = "MODULE_NAME"
private const val SERVICE_ENV_FALLBACK = "SHIKKANIME_SERVICE"
private const val SESSION_TOKEN_ENV = "SHIKKANIME_SESSION_TOKEN"
private const val SESSIONS_DIRECTORY = "sessions"
private val DEFAULT_EXCLUDED_PATHS = setOf("/health", "/swagger", "/openapi", "/v1/session-traces")

/**
 * Configuration of the session tracking installed by `configureDefaultModules`.
 *
 * @property enabled whether requests are tracked at all. Disable it in tests or in an application
 * that must not write a journal.
 * @property directory directory holding the per-service session directories. When `null`, the
 * framework uses `$SHIKKANIME_LOGS_DIR/sessions`, or `<working directory>/logs/sessions`.
 * @property service name of this service in the journal. When `null`, the framework reads
 * `MODULE_NAME`, then `SHIKKANIME_SERVICE`, and falls back to `default`.
 * @property retention how long a session file is kept after its last event.
 * @property generateIfMissing whether a request without a session header starts a new session.
 * When `false`, only requests carrying a valid header are tracked.
 * @property store custom persistent store. When set, [directory] and [retention] are ignored and
 * the caller owns the store lifecycle.
 * @property apiToken bearer token protecting the read API. When `null` or blank, the API is not
 * registered. Defaults to `SHIKKANIME_SESSION_TOKEN`.
 * @property excludedPathPrefixes request paths ignored by tracking, compared by prefix.
 */
data class SessionConfig(
    val enabled: Boolean = true,
    val directory: Path? = null,
    val service: String? = null,
    val retention: Duration = Duration.ofDays(7),
    val generateIfMissing: Boolean = true,
    val store: SessionStore? = null,
    val apiToken: String? = System.getenv(SESSION_TOKEN_ENV),
    val excludedPathPrefixes: Set<String> = DEFAULT_EXCLUDED_PATHS
)

/**
 * Resolves the directory receiving the per-service session directories.
 *
 * @return the configured directory, or the framework default.
 */
internal fun SessionConfig.resolvedDirectory(): Path {
    directory?.let { return it }
    val base = System.getenv(LOG_DIRECTORY_ENV)?.takeIf { it.isNotBlank() }
        ?: Paths.get(System.getProperty("user.dir"), "logs").toString()
    return Paths.get(base).resolve(SESSIONS_DIRECTORY)
}

/**
 * Resolves the service name written in every event.
 *
 * @return a safe service name, never blank.
 */
internal fun SessionConfig.resolvedService(): String =
    SessionIds.service(service ?: System.getenv(SERVICE_ENV) ?: System.getenv(SERVICE_ENV_FALLBACK))
