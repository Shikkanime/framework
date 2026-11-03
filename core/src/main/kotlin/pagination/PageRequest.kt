package fr.shikkanime.core.pagination

/**
 * Requested slice of a collection.
 *
 * The page is one-based: page 1 is the first page, like the pagination the administration exposes.
 * The size is bounded so a client cannot ask the database for a whole table through one request.
 *
 * @property page one-based index of the requested page.
 * @property size maximum number of elements the page may hold.
 */
data class PageRequest(
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE
) {
    init {
        require(page >= 1) { "page must be at least 1" }
        require(size in 1..MAX_SIZE) { "size must be between 1 and $MAX_SIZE" }
    }

    /** Number of elements to skip before the requested page. */
    val offset: Long
        get() = (page - 1L) * size

    companion object {
        /** Page returned when the caller does not ask for one. */
        const val DEFAULT_PAGE = 1

        /** Number of elements returned when the caller does not ask for a size. */
        const val DEFAULT_SIZE = 20

        /** Largest page a client may request. */
        const val MAX_SIZE = 100

        /**
         * Builds a request from optional query parameters.
         *
         * A missing page starts at the first one and a missing size takes [defaultSize]. Values
         * outside the supported range are clamped instead of refused: pagination is a display
         * concern, and an old bookmark must still answer a page.
         *
         * @param page requested page, or `null` when the client did not send one.
         * @param size requested page size, or `null` when the client did not send one.
         * @param defaultSize page size used when [size] is `null`.
         * @return the validated page request.
         */
        fun of(page: Int?, size: Int?, defaultSize: Int = DEFAULT_SIZE): PageRequest =
            PageRequest(
                page = (page ?: DEFAULT_PAGE).coerceAtLeast(DEFAULT_PAGE),
                size = (size ?: defaultSize).coerceIn(1, MAX_SIZE)
            )
    }
}
