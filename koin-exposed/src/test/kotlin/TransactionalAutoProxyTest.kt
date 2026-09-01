package fr.shikkanime.framework.koin

import fr.shikkanime.exposed.DatabaseWrapper
import fr.shikkanime.exposed.Transactional
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID

class TransactionalAutoProxyTest {
    interface TransactionAwareService {
        @Transactional
        fun isTransactionActive(): Boolean
    }

    class TransactionAwareServiceImpl : TransactionAwareService {
        override fun isTransactionActive(): Boolean =
            TransactionManager.currentOrNull() != null
    }

    interface PlainService {
        fun id(): String
    }

    class PlainServiceImpl : PlainService {
        override fun id(): String =
            "plain"
    }

    interface AmbiguousFirstService {
        @Transactional
        fun first(): Int
    }

    interface AmbiguousSecondService {
        @Transactional
        fun second(): Int
    }

    class AmbiguousServiceImpl : AmbiguousFirstService, AmbiguousSecondService {
        override fun first(): Int = 1
        override fun second(): Int = 2
    }

    @BeforeEach
    fun stopGlobalKoin() {
        stopKoin()
    }

    @Nested
    @DisplayName("Given a Koin registry with a @Transactional service")
    inner class GivenTransactionalService {
        @Test
        fun `should wrap the scanned implementation in a transactional proxy`() {
            // Given
            val databaseName = "auto_proxy_${UUID.randomUUID().toString().replace("-", "")}"
            DatabaseWrapper(
                jdbcUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1",
                driverClassName = "org.h2.Driver",
                maximumPoolSize = 1
            ).connect()

            val koin = startKoin {
                modules(
                    module {
                        single { TransactionAwareServiceImpl() }
                    }
                )
            }.koin

            // When
            val replaced = applyTransactionalProxies(koin)
            val resolved: TransactionAwareService = koin.get()

            // Then
            assertEquals(1, replaced)
            assertTrue(resolved is Proxy)
            assertTrue(resolved.isTransactionActive())
            koin.close()
        }
    }

    @Nested
    @DisplayName("Given a Koin registry without transactional services")
    inner class GivenPlainService {
        @Test
        fun `should leave plain implementations untouched`() {
            // Given
            val koin = startKoin {
                modules(
                    module {
                        single<PlainService> { PlainServiceImpl() }
                    }
                )
            }.koin

            // When
            val replaced = applyTransactionalProxies(koin)
            val resolved: PlainService = koin.get()

            // Then
            assertEquals(0, replaced)
            assertTrue(resolved !is Proxy)
            koin.close()
        }
    }

    @Nested
    @DisplayName("Given a Koin registry with an implementation of two @Transactional interfaces")
    inner class GivenAmbiguousImplementation {
        @Test
        fun `should fail with an explicit error`() {
            // Given
            val koin = startKoin {
                modules(
                    module {
                        single { AmbiguousServiceImpl() }
                    }
                )
            }.koin

            // When / Then
            try {
                applyTransactionalProxies(koin)
            } catch (exception: IllegalStateException) {
                assertTrue(exception.message!!.contains("several @Transactional interfaces"))
            } finally {
                koin.close()
            }
        }
    }
}
