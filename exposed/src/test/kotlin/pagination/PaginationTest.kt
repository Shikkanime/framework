package fr.shikkanime.exposed.pagination

import fr.shikkanime.core.pagination.PageRequest
import fr.shikkanime.exposed.DatabaseWrapper
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@DisplayName("tests for the query pagination")
class PaginationTest {

    private object ItemsTable : Table("pagination_items") {
        val id = integer("id")
        val label = varchar("label", 32)

        override val primaryKey = PrimaryKey(id)
    }

    private lateinit var databaseWrapper: DatabaseWrapper

    @BeforeEach
    fun setUp() {
        databaseWrapper = DatabaseWrapper(
            jdbcUrl = "jdbc:h2:mem:${Uuid.random()};DB_CLOSE_DELAY=-1",
            driverClassName = "org.h2.Driver"
        )
        databaseWrapper.addTables(ItemsTable)
        databaseWrapper.connect()
        databaseWrapper.initializeSchema()

        transaction {
            (1..5).forEach { id ->
                ItemsTable.insert {
                    it[ItemsTable.id] = id
                    it[label] = "item-$id"
                }
            }
        }
    }

    @Nested
    @DisplayName("tests for the pages of a query")
    inner class PageTests {
        @Test
        @DisplayName("should return the first page and the total number of rows")
        fun `should return the first page and the total number of rows`() {
            // Given
            val request = PageRequest(page = 1, size = 2)

            // When
            val page = transaction {
                ItemsTable.selectAll()
                    .orderBy(ItemsTable.id)
                    .paginate(request)
            }

            // Then
            assertEquals(listOf(1, 2), page.data.map { it[ItemsTable.id] })
            assertEquals(1, page.page)
            assertEquals(2, page.size)
            assertEquals(5L, page.totalElements)
            assertEquals(3, page.totalPages)
        }

        @Test
        @DisplayName("should return the requested page without reading the previous rows")
        fun `should return the requested page without reading the previous rows`() {
            // Given
            val request = PageRequest(page = 3, size = 2)

            // When
            val page = transaction {
                ItemsTable.selectAll()
                    .orderBy(ItemsTable.id)
                    .paginate(request)
            }

            // Then
            assertEquals(listOf(5), page.data.map { it[ItemsTable.id] })
            assertEquals(3, page.page)
            assertEquals(5L, page.totalElements)
        }

        @Test
        @DisplayName("should return an empty page when the requested page is past the last one")
        fun `should return an empty page when the requested page is past the last one`() {
            // Given
            val request = PageRequest(page = 10, size = 2)

            // When
            val page = transaction {
                ItemsTable.selectAll()
                    .orderBy(ItemsTable.id)
                    .paginate(request)
            }

            // Then
            assertTrue(page.data.isEmpty())
            assertEquals(10, page.page)
            assertEquals(5L, page.totalElements)
        }

        @Test
        @DisplayName("should return an empty page when the query matches nothing")
        fun `should return an empty page when the query matches nothing`() {
            // Given
            val request = PageRequest(page = 1, size = 20)

            // When
            val page = transaction {
                ItemsTable.selectAll()
                    .where { ItemsTable.id greaterEq 6 }
                    .paginate(request)
            }

            // Then
            assertTrue(page.data.isEmpty())
            assertEquals(0L, page.totalElements)
            assertEquals(0, page.totalPages)
        }
    }
}
