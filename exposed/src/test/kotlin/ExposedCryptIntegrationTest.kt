package fr.shikkanime.exposed

import org.jetbrains.exposed.v1.crypt.Argon2Hasher
import org.jetbrains.exposed.v1.crypt.BCryptHasher
import org.jetbrains.exposed.v1.crypt.Hasher
import org.jetbrains.exposed.v1.crypt.Pbkdf2Hasher
import org.jetbrains.exposed.v1.crypt.SCryptHasher
import org.jetbrains.exposed.v1.crypt.hashed
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.sql.DriverManager
import java.util.UUID
import java.util.stream.Stream

class ExposedCryptIntegrationTest {
    private lateinit var jdbcUrl: String
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        val databaseName = "crypt_${UUID.randomUUID().toString().replace("-", "")}"
        jdbcUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1"
        database = Database.connect(jdbcUrl, driver = "org.h2.Driver")
    }

    @AfterEach
    fun tearDown() {
        TransactionManager.closeAndUnregister(database)
    }

    private fun rawValue(tableName: String, id: Int): String? =
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT password FROM $tableName WHERE id = $id").use { result ->
                    if (result.next()) result.getString("password") else null
                }
            }
        }

    private class UserTable(hasher: Hasher) : Table("crypt_users_${System.nanoTime()}") {
        val idColumn = integer("id").autoIncrement()
        val password = text("password").hashed(hasher)

        override val primaryKey = PrimaryKey(idColumn)
    }

    private class NullableUserTable(hasher: Hasher) : Table("crypt_nullable_users_${System.nanoTime()}") {
        val idColumn = integer("id").autoIncrement()
        val password = text("password").nullable().hashed(hasher)

        override val primaryKey = PrimaryKey(idColumn)
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("tests for hashed password columns")
    inner class HashedColumnTests {

        private fun hashers(): Stream<Hasher> = Stream.of(
            BCryptHasher(),
            Argon2Hasher(),
            Pbkdf2Hasher(),
            SCryptHasher(),
        )

        @Test
        fun `should hash on insert and never store the plaintext password`() {
            // Given
            val hasher = BCryptHasher()
            val table = UserTable(hasher)
            transaction(database) {
                SchemaUtils.create(table)
                table.insert {
                    it[table.idColumn] = 1
                    it[table.password] = hasher.hash(PLAINTEXT)
                }
            }

            // When
            val stored = transaction(database) {
                table.selectAll().single()[table.password]
            }

            // Then
            assertTrue(stored.matches(PLAINTEXT))
            assertFalse(stored.matches(WRONG_PASSWORD))
            val raw = rawValue(table.tableName, 1)
            assertTrue(raw!!.startsWith("$2"), "raw stored value should be a bcrypt hash")
            assertNotEquals(PLAINTEXT, raw)
        }

        @ParameterizedTest
        @MethodSource("hashers")
        fun `should hash and match the plaintext for every supported hasher`(hasher: Hasher) {
            // Given
            val table = UserTable(hasher)
            transaction(database) {
                SchemaUtils.create(table)
                table.insert {
                    it[table.idColumn] = 1
                    it[table.password] = hasher.hash(PLAINTEXT)
                }
            }

            // When
            val stored = transaction(database) {
                table.selectAll().single()[table.password]
            }

            // Then
            assertTrue(stored.matches(PLAINTEXT))
            assertFalse(stored.matches(WRONG_PASSWORD))
            val raw = rawValue(table.tableName, 1)
            assertNotEquals(PLAINTEXT, raw)
        }

        @Test
        fun `should keep null for a nullable hashed column`() {
            // Given
            val hasher = BCryptHasher()
            val table = NullableUserTable(hasher)
            transaction(database) {
                SchemaUtils.create(table)
                table.insert {
                    it[table.idColumn] = 1
                    it[table.password] = null
                }
            }

            // When
            val stored = transaction(database) {
                table.selectAll().single()[table.password]
            }

            // Then
            assertNull(stored)
            val raw = rawValue(table.tableName, 1)
            assertNull(raw)
        }

        @Test
        fun `should stop matching the old password after an update`() {
            // Given
            val hasher = BCryptHasher()
            val table = UserTable(hasher)
            transaction(database) {
                SchemaUtils.create(table)
                table.insert {
                    it[table.idColumn] = 1
                    it[table.password] = hasher.hash(OLD_PASSWORD)
                }
            }

            // When
            transaction(database) {
                table.update({ table.idColumn eq 1 }) {
                    it[table.password] = hasher.hash(NEW_PASSWORD)
                }
            }
            val stored = transaction(database) {
                table.selectAll().single()[table.password]
            }

            // Then
            assertTrue(stored.matches(NEW_PASSWORD))
            assertFalse(stored.matches(OLD_PASSWORD))
            val raw = rawValue(table.tableName, 1)
            assertNotEquals(OLD_PASSWORD, raw)
            assertNotEquals(NEW_PASSWORD, raw)
        }
    }

    companion object {
        private const val PLAINTEXT = "s3cret"
        private const val WRONG_PASSWORD = "wrong-password"
        private const val OLD_PASSWORD = "old-password"
        private const val NEW_PASSWORD = "new-password"
    }
}
