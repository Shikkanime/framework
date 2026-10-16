package fr.shikkanime.ktor

/**
 * Indicates that a request parameter could not be converted to the type the controller expects.
 *
 * A malformed path, query or body parameter is a client error, not a server failure: the request
 * handler translates it to an HTTP 400 carrying a [fr.shikkanime.ktor.dtos.MessageDto] error.
 *
 * @property message human-readable description naming the parameter and the expected type.
 */
class InvalidParameterException(override val message: String) : IllegalArgumentException(message)
