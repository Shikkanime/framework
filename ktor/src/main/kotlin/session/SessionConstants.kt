package fr.shikkanime.ktor.session

import kotlinx.serialization.json.Json

/** Nanoseconds in one millisecond. */
internal const val NANOS_PER_MILLI = 1_000_000L

/** Extension of a session journal file. */
internal const val SESSION_FILE_EXTENSION = ".jsonl"

/** Maximum length of an exception summary written in a journal event. */
internal const val MAX_ERROR_LENGTH = 512

/**
 * JSON format of one journal line: compact, without default or `null` properties, and tolerant to
 * properties added by a newer framework version.
 */
internal val SESSION_JSON: Json = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}
