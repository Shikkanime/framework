package fr.shikkanime.cache

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {

    @Nested
    @DisplayName("tests for concurrent request deduplication")
    inner class DeduplicationTests {

        @Test
        suspend fun `should execute loader once and share result among concurrent callers`(): Unit = coroutineScope {
            // Given
            val singleFlight = SingleFlight()
            val callCounter = AtomicInteger(0)

            val loader: suspend () -> String = {
                callCounter.incrementAndGet()
                delay(50)
                "Shared Result"
            }

            // When
            val task1 = async { singleFlight.doOnce("shared_key", loader) }
            val task2 = async { singleFlight.doOnce("shared_key", loader) }
            val task3 = async { singleFlight.doOnce("shared_key", loader) }

            val res1 = task1.await()
            val res2 = task2.await()
            val res3 = task3.await()

            // Then
            assertEquals(1, callCounter.get())
            assertEquals("Shared Result", res1)
            assertEquals("Shared Result", res2)
            assertEquals("Shared Result", res3)
        }

        @Test
        suspend fun `should execute loader again for subsequent sequential calls`() {
            // Given
            val singleFlight = SingleFlight()
            val callCounter = AtomicInteger(0)

            val loader: suspend () -> Int = { callCounter.incrementAndGet() }

            // When
            val res1 = singleFlight.doOnce("seq_key", loader)
            val res2 = singleFlight.doOnce("seq_key", loader)

            // Then
            assertEquals(2, callCounter.get())
            assertEquals(1, res1)
            assertEquals(2, res2)
        }

        @Test
        suspend fun `should execute loaders independently for different keys`(): Unit = coroutineScope {
            // Given
            val singleFlight = SingleFlight()
            val counterA = AtomicInteger(0)
            val counterB = AtomicInteger(0)

            // When
            val taskA = async { singleFlight.doOnce("key_a") { counterA.incrementAndGet(); "A" } }
            val taskB = async { singleFlight.doOnce("key_b") { counterB.incrementAndGet(); "B" } }

            val resA = taskA.await()
            val resB = taskB.await()

            // Then
            assertEquals(1, counterA.get())
            assertEquals(1, counterB.get())
            assertEquals("A", resA)
            assertEquals("B", resB)
        }
    }

    @Nested
    @DisplayName("tests for exception handling")
    inner class ExceptionHandlingTests {

        @Test
        suspend fun `should propagate exception to all waiting callers when loader fails`(): Unit = coroutineScope {
            // Given
            val singleFlight = SingleFlight()
            val callCounter = AtomicInteger(0)

            val failingLoader: suspend () -> String = {
                callCounter.incrementAndGet()
                delay(30)
                throw IllegalStateException("Loader failure")
            }

            // When
            val task1 = async { runCatching { singleFlight.doOnce("fail_key", failingLoader) } }
            val task2 = async { runCatching { singleFlight.doOnce("fail_key", failingLoader) } }

            val res1 = task1.await()
            val res2 = task2.await()

            // Then
            assertEquals(1, callCounter.get())
            assertTrue(res1.isFailure)
            assertTrue(res2.isFailure)
            assertEquals("Loader failure", res1.exceptionOrNull()?.message)
            assertEquals("Loader failure", res2.exceptionOrNull()?.message)
        }
    }
}
