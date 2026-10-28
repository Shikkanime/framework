package fr.shikkanime.ktor.session

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

private const val MIN_ID_LENGTH = 8
private const val MAX_ID_LENGTH = 64
private const val MIN_SERVICE_LENGTH = 1
private const val MAX_SERVICE_LENGTH = 32
private const val DEFAULT_SERVICE = "default"
private const val VERSION_MASK = -0xF001L
private const val VERSION_4 = 0x0000000000004000L
private const val VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL

/**
 * Validation and generation of the correlation identifiers.
 *
 * Identifiers become file names in [FileSessionStore], so only a strict, path-safe alphabet is
 * accepted. A rejected value is replaced by a generated identifier instead of being refused: a
 * malformed correlation header must not fail the business request.
 */
internal object SessionIds {
    /**
     * Tells whether [value] can be used as a session or request identifier.
     *
     * @param value candidate identifier.
     * @return `true` when the value is 8 to 64 path-safe characters.
     */
    fun isValid(value: String?): Boolean =
        value != null && value.length in MIN_ID_LENGTH..MAX_ID_LENGTH && value.all(::isAllowed)

    /**
     * Tells whether [value] can be used as a service name.
     *
     * @param value candidate service name.
     * @return `true` when the value is 1 to 32 path-safe characters.
     */
    fun isValidService(value: String?): Boolean =
        value != null && value.length in MIN_SERVICE_LENGTH..MAX_SERVICE_LENGTH && value.all(::isAllowed)

    /**
     * Returns [value] when it is a valid session identifier, or a new secure identifier.
     *
     * @param value identifier sent by the client.
     * @return a valid session identifier.
     */
    fun resolveSession(value: String?): String =
        value?.takeIf(::isValid) ?: UUID.randomUUID().toString()

    /**
     * Returns [value] when it is a valid request identifier, or a new fast identifier.
     *
     * Request identifiers need uniqueness, not unpredictability: the secure generator is reserved
     * for session identifiers, which can be exposed to a client.
     *
     * @param value identifier sent by the client.
     * @return a valid request identifier.
     */
    fun resolveRequest(value: String?): String =
        value?.takeIf(::isValid) ?: newRequestId()

    /**
     * Returns [value] when it is a valid service name, or `default`.
     *
     * @param value candidate service name.
     * @return a valid service name.
     */
    fun service(value: String?): String =
        value?.takeIf(::isValidService) ?: DEFAULT_SERVICE

    /**
     * Generates a version 4 UUID without the contention of [UUID.randomUUID].
     *
     * @return a new request identifier.
     */
    fun newRequestId(): String {
        val random = ThreadLocalRandom.current()
        val most = random.nextLong() and VERSION_MASK or VERSION_4
        val least = random.nextLong() and VARIANT_MASK or Long.MIN_VALUE
        return UUID(most, least).toString()
    }

    private fun isAllowed(character: Char): Boolean =
        when (character) {
            in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_' -> true
            else -> false
        }
}
