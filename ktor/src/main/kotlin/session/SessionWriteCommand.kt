package fr.shikkanime.ktor.session

import java.util.concurrent.CountDownLatch

/**
 * Command processed by the [SessionWriter] thread.
 */
internal sealed interface SessionWriteCommand {
    /** Append one event. */
    data class Event(val event: SessionEvent) : SessionWriteCommand

    /** Flush every open file, then release [latch]. */
    data class Flush(val latch: CountDownLatch) : SessionWriteCommand

    /** Flush, close every file, and stop the writer. */
    data object Close : SessionWriteCommand
}
