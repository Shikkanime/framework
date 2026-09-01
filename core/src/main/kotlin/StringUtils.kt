package fr.shikkanime.core

import kotlin.random.Random

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
    const val ALPHABET_SPECIAL = "_-.!~*'();:@&=+$,/?#[]%"

    /** An empty string constant for convenience. */
    const val EMPTY = ""

    /**
     * Generates a random string using uppercase letters, lowercase letters, numbers,
     * and optionally special characters.
     *
     * The generated string always has the requested [length]. When enabled, each
     * requested character category is guaranteed to occur at least once. The
     * remaining characters are selected randomly from the complete allowed alphabet,
     * and the resulting characters are shuffled before being returned.
     *
     * @param length the required length of the generated string; must be greater than zero
     * @param includeSpecial whether special characters may be included in the generated string
     * @param shouldHaveAtLeastOneUppercase whether the result must contain at least one uppercase letter
     * @param shouldHaveAtLeastOneLowercase whether the result must contain at least one lowercase letter
     * @param shouldHaveAtLeastOneNumber whether the result must contain at least one number
     * @param shouldHaveAtLeastOneSpecial whether the result must contain at least one special character;
     * this option has no effect when [includeSpecial] is `false`
     * @param random the random number generator to use for character selection; defaults to [Random.Default]
     * @return a randomly generated string matching the requested length and character requirements
     * @throws IllegalArgumentException if [length] is not greater than zero or is shorter than
     * the number of required character categories
     */
    fun generateRandomString(
        length: Int,
        includeSpecial: Boolean = true,
        shouldHaveAtLeastOneUppercase: Boolean = false,
        shouldHaveAtLeastOneLowercase: Boolean = false,
        shouldHaveAtLeastOneNumber: Boolean = false,
        shouldHaveAtLeastOneSpecial: Boolean = false,
        random: Random = Random.Default
    ): String {
        require(length > 0) { "Length must be greater than 0" }
        require(includeSpecial || !shouldHaveAtLeastOneSpecial) { "Cannot require at least one special character when special characters are not included" }

        var requiredCharacterTypeCount = 0
        if (shouldHaveAtLeastOneUppercase) requiredCharacterTypeCount++
        if (shouldHaveAtLeastOneLowercase) requiredCharacterTypeCount++
        if (shouldHaveAtLeastOneNumber) requiredCharacterTypeCount++
        if (includeSpecial && shouldHaveAtLeastOneSpecial) requiredCharacterTypeCount++
        require(requiredCharacterTypeCount <= length) { "Length must be greater than or equal to the number of required character types" }

        val alphabet = ALPHABET_UPPERCASE +
                ALPHABET_LOWERCASE +
                ALPHABET_NUMBERS +
                if (includeSpecial) ALPHABET_SPECIAL else EMPTY

        return buildList(length) {
            if (shouldHaveAtLeastOneUppercase) add(ALPHABET_UPPERCASE.random(random))
            if (shouldHaveAtLeastOneLowercase) add(ALPHABET_LOWERCASE.random(random))
            if (shouldHaveAtLeastOneNumber) add(ALPHABET_NUMBERS.random(random))
            if (includeSpecial && shouldHaveAtLeastOneSpecial) add(ALPHABET_SPECIAL.random(random))

            repeat(length - size) {
                add(alphabet.random(random))
            }
        }.shuffled(random)
            .joinToString(EMPTY)
    }
}