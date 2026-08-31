package fr.shikkanime.core

/**
 * Provides utilities for generating strings.
 */
object StringUtils {
    /** Uppercase characters available for random string generation. */
    const val ALPHABET_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** Lowercase characters available for random string generation. */
    const val ALPHABET_LOWERCASE = "abcdefghijklmnopqrstuvwxyz"

    /** Numeric characters available for random string generation. */
    const val ALPHABET_NUMBERS = "0123456789"

    /** Special characters available for random string generation. */
    const val ALPHABET_SPECIAL = "_-.'!~*'();:@&=+$,/?#[]%"

    /**
     * Generates a random string with the requested length and character constraints.
     *
     * The special-character requirement is applied only when [includeSpecial] is `true`.
     *
     * @throws IllegalArgumentException if [length] is not positive or cannot accommodate all required character types
     */
    fun generateRandomString(
        length: Int,
        includeSpecial: Boolean = true,

        shouldHaveAtLeastOneUppercase: Boolean = false,
        shouldHaveAtLeastOneLowercase: Boolean = false,
        shouldHaveAtLeastOneNumber: Boolean = false,
        shouldHaveAtLeastOneSpecial: Boolean = false
    ): String {
        require(length > 0) { "Length must be greater than 0" }

        val requiredCharacterTypeCount = listOf(
            shouldHaveAtLeastOneUppercase,
            shouldHaveAtLeastOneLowercase,
            shouldHaveAtLeastOneNumber,
            includeSpecial && shouldHaveAtLeastOneSpecial
        ).count { it }
        require(requiredCharacterTypeCount <= length) { "Length must be greater than or equal to the number of required character types" }

        val alphabet = ALPHABET_UPPERCASE +
                ALPHABET_LOWERCASE +
                ALPHABET_NUMBERS +
                if (includeSpecial) ALPHABET_SPECIAL else ""

        return buildList(length) {
            if (shouldHaveAtLeastOneUppercase) add(ALPHABET_UPPERCASE.random())
            if (shouldHaveAtLeastOneLowercase) add(ALPHABET_LOWERCASE.random())
            if (shouldHaveAtLeastOneNumber) add(ALPHABET_NUMBERS.random())
            if (includeSpecial && shouldHaveAtLeastOneSpecial) add(ALPHABET_SPECIAL.random())

            repeat(length - size) {
                add(alphabet.random())
            }
        }.shuffled()
            .joinToString("")
    }
}