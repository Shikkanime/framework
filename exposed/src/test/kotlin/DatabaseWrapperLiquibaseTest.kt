package fr.shikkanime.exposed

import java.sql.DriverManager
import java.util.*
import kotlin.test.*

class DatabaseWrapperLiquibaseTest {
    private lateinit var jdbcUrl: String
    private lateinit var databaseWrapper: DatabaseWrapper

    @BeforeTest
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

    @Test
    fun `migrateSchema applies the Liquibase changelog`() {
        databaseWrapper.migrateSchema(TEST_CHANGELOG)

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
    fun `migrateSchema does not apply an executed changeset twice`() {
        databaseWrapper.migrateSchema(TEST_CHANGELOG)
        databaseWrapper.migrateSchema(TEST_CHANGELOG)

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

    private companion object {
        const val TEST_CHANGELOG = "db/changelog/database-wrapper-test.xml"
    }
}
