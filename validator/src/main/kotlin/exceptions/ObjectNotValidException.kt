package fr.shikkanime.validator.exceptions

/**
 * Indicates that an object failed one or more framework validation constraints.
 *
 * @property message semicolon-separated validation errors collected by the validator.
 */
class ObjectNotValidException(override val message: String) : Exception(message)