package fr.shikkanime.ktor.session

/**
 * Formats a throwable as the bounded summary stored in a session event.
 *
 * The stack trace is deliberately omitted: the journal is a timeline, and a full trace belongs to
 * the log files.
 *
 * @return exception type and message, truncated to [MAX_ERROR_LENGTH] characters.
 */
internal fun Throwable.toSessionError(): String {
    val type = this::class.qualifiedName ?: this::class.simpleName ?: "Error"
    val detail = this.message?.let { ": $it" } ?: ""
    return (type + detail).take(MAX_ERROR_LENGTH)
}
