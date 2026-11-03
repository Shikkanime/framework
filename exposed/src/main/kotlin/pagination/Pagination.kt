package fr.shikkanime.exposed.pagination

import fr.shikkanime.core.pagination.Page
import fr.shikkanime.core.pagination.PageRequest
import org.jetbrains.exposed.v1.jdbc.SizedIterable

/**
 * Reads one page of this query and counts every matching row.
 *
 * The count runs before the window is applied, so [Page.totalElements] is the number of matches and
 * not the size of the page. The window itself is read with `limit` and `offset`, which keeps the
 * database from materialising the rows the client will never see.
 *
 * @param request page and size to read.
 * @return the requested page with the total number of matches.
 */
fun <T> SizedIterable<T>.paginate(request: PageRequest): Page<T> {
    val totalElements = count()
    val data = if (totalElements == 0L) {
        emptyList()
    } else {
        limit(request.size)
            .offset(request.offset)
            .toList()
    }

    return Page(data, request.page, request.size, totalElements)
}
