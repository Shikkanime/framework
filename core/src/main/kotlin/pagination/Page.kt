package fr.shikkanime.core.pagination

/**
 * One page of a collection, with the bounds a client needs to navigate it.
 *
 * [totalElements] is the number of rows the query matches, not the number of rows this page holds:
 * it is what lets a client display the total and compute the last page without reading the rest of
 * the result.
 *
 * @property data elements of the requested page.
 * @property page one-based index of this page.
 * @property size maximum number of elements a page may hold.
 * @property totalElements number of elements matching the query, all pages included.
 */
data class Page<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
) {
    /** Number of pages the matching elements span; zero when the query matches nothing. */
    val totalPages: Int
        get() = if (totalElements == 0L) 0 else ((totalElements + size - 1) / size).toInt()

    /** Whether a page follows this one. */
    val hasNext: Boolean
        get() = page < totalPages

    /** Whether a page precedes this one. */
    val hasPrevious: Boolean
        get() = page > 1

    /**
     * Maps every element while keeping the bounds of this page.
     *
     * @param transform conversion applied to each element.
     * @return the same page holding the converted elements.
     */
    fun <R> map(transform: (T) -> R): Page<R> =
        Page(
            data = data.map(transform),
            page = page,
            size = size,
            totalElements = totalElements
        )
}
