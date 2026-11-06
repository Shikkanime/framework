package fr.shikkanime.ktor.pagination

import fr.shikkanime.core.pagination.PageRequest
import fr.shikkanime.validator.Max
import fr.shikkanime.validator.Min

/**
 * Query parameters of a paged endpoint, bound and validated by the framework.
 *
 * Declare `@Valid @ModelAttribute query: PageQuery` on the endpoint: the framework reads `page` and
 * `size` from the query string, documents both in the OpenAPI description, and answers HTTP 400 when
 * the page is below [PageRequest.MIN_PAGE] or the size outside
 * [PageRequest.MIN_SIZE]..[PageRequest.MAX_SIZE] - a request that must never reach the database with
 * a negative page. [toPageRequest] then hands the validated bounds to the core type.
 *
 * @property page one-based index of the requested page.
 * @property size maximum number of elements the page may hold.
 * @see PageRequest
 */
data class PageQuery(
    @Min(PageRequest.MIN_PAGE) val page: Int = PageRequest.DEFAULT_PAGE,
    @Min(PageRequest.MIN_SIZE) @Max(PageRequest.MAX_SIZE) val size: Int = PageRequest.DEFAULT_SIZE
) {
    /**
     * Converts the validated bounds to the core page request.
     *
     * @return the page request carrying these bounds.
     * @see PageRequest
     */
    fun toPageRequest(): PageRequest =
        PageRequest(page, size)
}
