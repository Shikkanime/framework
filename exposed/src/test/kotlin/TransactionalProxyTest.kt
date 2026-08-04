package fr.shikkanime.exposed

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TransactionalProxyTest {
    @Test
    fun `should use the wrapper associated with the current database by default`() {
        // Given
        val databaseWrapper = createDatabaseWrapper()
        databaseWrapper.connect()
        val service = TransactionalProxy(
            TransactionAwareServiceImpl(),
            TransactionAwareService::class.java
        ).create()

        // When
        val transactionActive = service.isTransactionActive()

        // Then
        assertTrue(transactionActive)
    }

    @Test
    fun `should fail when the current database has no wrapper`() {
        // Given
        val databaseName = "unmanaged_${UUID.randomUUID().toString().replace("-", "")}"
        Database.connect(
            url = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        // When / Then
        assertThrows(IllegalStateException::class.java) {
            TransactionalProxy(
                TransactionAwareServiceImpl(),
                TransactionAwareService::class.java
            )
        }
    }

    @Test
    fun `should keep supporting an explicit database wrapper`() {
        // Given
        val databaseWrapper = createDatabaseWrapper()
        databaseWrapper.connect()
        val service = TransactionalProxy(
            databaseWrapper,
            TransactionAwareServiceImpl(),
            TransactionAwareService::class.java
        ).create()

        // When
        val transactionActive = service.isTransactionActive()

        // Then
        assertTrue(transactionActive)
    }

    private fun createDatabaseWrapper(): DatabaseWrapper {
        val databaseName = "transactional_proxy_${UUID.randomUUID().toString().replace("-", "")}"
        return DatabaseWrapper(
            jdbcUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1",
            driverClassName = "org.h2.Driver",
            maximumPoolSize = 1
        )
    }

    interface TransactionAwareService {
        @Transactional
        fun isTransactionActive(): Boolean
    }

    class TransactionAwareServiceImpl : TransactionAwareService {
        override fun isTransactionActive(): Boolean =
            TransactionManager.currentOrNull() != null
    }
}
