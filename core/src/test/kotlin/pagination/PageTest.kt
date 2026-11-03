package fr.shikkanime.core.pagination

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("tests for the pagination bounds")
class PageTest {

    @Nested
    @DisplayName("tests for PageRequest")
    inner class PageRequestTests {
        @Test
        @DisplayName("should start at the first page with the default size")
        fun `should start at the first page with the default size`() {
            // Given
            val request = PageRequest()

            // When
            val offset = request.offset

            // Then
            assertEquals(1, request.page)
            assertEquals(PageRequest.DEFAULT_SIZE, request.size)
            assertEquals(0L, offset)
        }

        @Test
        @DisplayName("should compute the offset of the requested page")
        fun `should compute the offset of the requested page`() {
            // Given
            val request = PageRequest(page = 3, size = 25)

            // When
            val offset = request.offset

            // Then
            assertEquals(50L, offset)
        }

        @Test
        @DisplayName("should clamp missing and out-of-range query parameters")
        fun `should clamp missing and out-of-range query parameters`() {
            // Given
            // When
            val request = PageRequest.of(page = 0, size = 1_000)

            // Then
            assertEquals(1, request.page)
            assertEquals(PageRequest.MAX_SIZE, request.size)
        }

        @Test
        @DisplayName("should refuse a page below one")
        fun `should refuse a page below one`() {
            // Given
            // When / Then
            assertThrows<IllegalArgumentException> { PageRequest(page = 0) }
        }

        @Test
        @DisplayName("should refuse a size outside the supported range")
        fun `should refuse a size outside the supported range`() {
            // Given
            // When / Then
            assertThrows<IllegalArgumentException> { PageRequest(size = 0) }
            assertThrows<IllegalArgumentException> { PageRequest(size = PageRequest.MAX_SIZE + 1) }
        }
    }

    @Nested
    @DisplayName("tests for Page")
    inner class PageTests {
        @Test
        @DisplayName("should compute the total number of pages")
        fun `should compute the total number of pages`() {
            // Given
            val page = Page(data = listOf(1, 2), page = 1, size = 2, totalElements = 5)

            // When
            val totalPages = page.totalPages

            // Then
            assertEquals(3, totalPages)
        }

        @Test
        @DisplayName("should report no page when the query matches nothing")
        fun `should report no page when the query matches nothing`() {
            // Given
            val page = Page(data = emptyList<Int>(), page = 1, size = 20, totalElements = 0)

            // When / Then
            assertEquals(0, page.totalPages)
            assertFalse(page.hasNext)
            assertFalse(page.hasPrevious)
        }

        @Test
        @DisplayName("should report the neighbouring pages")
        fun `should report the neighbouring pages`() {
            // Given
            val page = Page(data = listOf(3, 4), page = 2, size = 2, totalElements = 5)

            // When / Then
            assertTrue(page.hasNext)
            assertTrue(page.hasPrevious)
        }

        @Test
        @DisplayName("should keep the bounds when the elements are mapped")
        fun `should keep the bounds when the elements are mapped`() {
            // Given
            val page = Page(data = listOf(1, 2), page = 2, size = 2, totalElements = 7)

            // When
            val mapped = page.map { it.toString() }

            // Then
            assertEquals(listOf("1", "2"), mapped.data)
            assertEquals(page.page, mapped.page)
            assertEquals(page.size, mapped.size)
            assertEquals(page.totalElements, mapped.totalElements)
        }
    }
}
