package fr.shikkanime.ktor.pagination

import fr.shikkanime.core.pagination.Page
import kotlinx.serialization.Serializable

/**
 * JSON view of a [Page].
 *
 * The payload repeats the bounds a client needs to navigate the result: the page it asked for, the
 * size it asked for, the number of pages and the total number of elements. A client therefore
 * renders its paginator from one response and never counts a collection it did not read.
 *
 * @property data elements of the requested page.
 * @property page one-based index of the returned page.
 * @property size maximum number of elements a page may hold.
 * @property totalPages number of pages the query matches span.
 * @property totalElements number of elements matching the query, all pages included.
 * @see [Page]
 */
@Serializable
data class PageDto<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long
)

/**
 * Maps a page and its elements to their JSON view.
 *
 * @param transform conversion applied to each element.
 * @return the JSON view of this page.
 */
fun <T, R> Page<T>.toDto(transform: (T) -> R): PageDto<R> =
    PageDto(
        data = data.map(transform),
        page = page,
        size = size,
        totalPages = totalPages,
        totalElements = totalElements
    )
