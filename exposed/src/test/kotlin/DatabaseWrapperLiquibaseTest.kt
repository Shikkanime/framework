package fr.shikkanime.exposed

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.*

class DatabaseWrapperLiquibaseTest {
    private lateinit var jdbcUrl: String
    private lateinit var databaseWrapper: DatabaseWrapper

    @BeforeEach
    fun setUp() {
        val databaseName = "liquibase_${UUID.randomUUID().toString().replace("-", "")}"
        jdbcUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1"
        databaseWrapper = DatabaseWrapper(
            jdbcUrl = jdbcUrl,
            driverClassName = "org.h2.Driver",
            maximumPoolSize = 1
        )
        databaseWrapper.connect()
    }

    @Nested
    @DisplayName("tests for Liquibase schema migrations")
    inner class MigrateSchemaTests {
        @Test
        fun `should apply the Liquibase changelog successfully`() {
            // Given
            val changelog = TEST_CHANGELOG

            // When
            databaseWrapper.migrateSchema(changelog)

            // Then
            DriverManager.getConnection(jdbcUrl).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT entry_name FROM liquibase_test_entry WHERE id = 1"
                    ).use { result ->
                        assertTrue(result.next())
                        assertEquals("Created by Liquibase", result.getString("entry_name"))
                        assertFalse(result.next())
                    }
                }
            }
        }

        @Test
        fun `should not apply an executed changeset twice when called multiple times`() {
            // Given
            val changelog = TEST_CHANGELOG

            // When
            databaseWrapper.migrateSchema(changelog)
            databaseWrapper.migrateSchema(changelog)

            // Then
            DriverManager.getConnection(jdbcUrl).use { connection ->
                connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM DATABASECHANGELOG
                    WHERE ID = ? AND AUTHOR = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, "create-liquibase-test-entry")
                    statement.setString(2, "shikkanime")

                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(1, result.getInt(1))
                    }
                }
            }
        }
    }

    private companion object {
        const val TEST_CHANGELOG = "db/changelog/database-wrapper-test.xml"
    }
}
