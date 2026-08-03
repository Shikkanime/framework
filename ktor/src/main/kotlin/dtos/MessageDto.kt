package fr.shikkanime.ktor.dtos

import kotlinx.serialization.Serializable

/**
 * Serializable base type for framework-generated HTTP messages.
 *
 * @property status machine-readable message category.
 * @property message human-readable message content.
 */
@Serializable
sealed class MessageDto {
    abstract val status: Status
    abstract val message: String

    /**
     * Categories supported by framework-generated messages.
     */
    enum class Status {
        /** Indicates that the request could not be processed successfully. */
        ERROR
    }

    /**
     * Factory methods for framework-generated messages.
     */
    companion object {
        /**
         * Creates an error message.
         *
         * @param message human-readable error description.
         * @return serializable error response.
         */
        fun error(message: String): ErrorMessageDto =
            ErrorMessageDto(message = message)
    }
}

/**
 * Serializable error payload returned for validation and unexpected controller failures.
 *
 * @property status error category, fixed to [MessageDto.Status.ERROR] by default.
 * @property message human-readable error description.
 */
@Serializable
data class ErrorMessageDto(
    override val status: Status = Status.ERROR,
    override val message: String
) : MessageDto()
