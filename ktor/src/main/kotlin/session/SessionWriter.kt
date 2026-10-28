package fr.shikkanime.ktor.session

import java.io.BufferedWriter
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val MAX_OPEN_WRITERS = 64
private const val CLOSE_TIMEOUT_MILLIS = 5_000L
private const val CLOSE_OFFER_ATTEMPTS = 5_000
private const val THREAD_NAME = "shikkanime-session-writer"

/**
 * Single-threaded append writer for one service directory.
 *
 * The request thread only offers an event to a bounded queue; serialization and disk writes happen
 * on the writer thread. A full queue drops the event instead of slowing the request down. The queue
 * is flushed when it drains, so a burst is written in batches and an idle service flushes each
 * event.
 *
 * @param directory service directory receiving the journal files.
 * @param retention how long an inactive journal file is kept.
 * @param queueCapacity maximum number of events waiting for the writer.
 * @param clock clock used by the retention policy.
 */
internal class SessionWriter(
    private val directory: Path,
    private val retention: Duration,
    queueCapacity: Int,
    private val clock: Clock
) {
    private val queue = ArrayBlockingQueue<SessionWriteCommand>(queueCapacity)
    private val writers = object : LinkedHashMap<String, BufferedWriter>(MAX_OPEN_WRITERS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedWriter>): Boolean {
            if (size <= MAX_OPEN_WRITERS) return false
            eldest.value.close()
            return true
        }
    }
    private val thread = Thread(::run, THREAD_NAME).apply {
        isDaemon = true
        // The writer is I/O-bound background work: it must yield the CPU to request threads.
        priority = Thread.MIN_PRIORITY
    }
    private val droppedEvents = AtomicLong()
    private var lastPurge: LocalDate? = null

    @Volatile private var accepting = true

    init {
        thread.start()
    }

    /**
     * Offers one event to the writer.
     *
     * @param event event to write.
     */
    fun append(event: SessionEvent) {
        if (!accepting || !SessionIds.isValid(event.sessionId)) return
        if (!queue.offer(SessionWriteCommand.Event(event))) droppedEvents.incrementAndGet()
    }

    /**
     * Returns the number of events dropped because the queue was full.
     *
     * @return the number of dropped events since the writer started.
     */
    fun droppedEvents(): Long =
        droppedEvents.get()

    /** Waits until every queued event is written and every open file is flushed. */
    fun flush() {
        if (!accepting || !thread.isAlive) return
        val latch = CountDownLatch(1)
        if (!queue.offer(SessionWriteCommand.Flush(latch))) return
        latch.await(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    }

    /** Writes the queued events, closes every file and stops the writer thread. */
    fun close() {
        if (!accepting) return
        accepting = false
        var attempts = 0
        while (!queue.offer(SessionWriteCommand.Close)) {
            if (!thread.isAlive || attempts++ >= CLOSE_OFFER_ATTEMPTS) break
            try {
                Thread.sleep(1)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        thread.join(CLOSE_TIMEOUT_MILLIS)
        if (thread.isAlive) thread.interrupt()
    }

    private fun run() {
        try {
            purgeIfNeeded()
            while (true) {
                when (val command = queue.take()) {
                    is SessionWriteCommand.Event -> {
                        write(command.event)
                        if (queue.isEmpty()) flushAll()
                    }

                    is SessionWriteCommand.Flush -> {
                        flushAll()
                        command.latch.countDown()
                    }

                    SessionWriteCommand.Close -> {
                        flushAll()
                        closeAll()
                        return
                    }
                }
                purgeIfNeeded()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            flushAll()
            closeAll()
        }
    }

    private fun write(event: SessionEvent) {
        try {
            val writer = writers.getOrPut(event.sessionId) { open(event.sessionId) }
            writer.write(SESSION_JSON.encodeToString(SessionEvent.serializer(), event))
            writer.newLine()
        } catch (_: Exception) {
            // A failed event must never stop the writer or fail the request that produced it.
        }
    }

    private fun open(sessionId: String): BufferedWriter {
        val file = directory.resolve(sessionId + SESSION_FILE_EXTENSION)
        val needsNewline = Files.exists(file) && Files.size(file) > 0 && !endsWithNewline(file)
        val writer = Files.newBufferedWriter(file, UTF_8, CREATE, APPEND)
        if (needsNewline) writer.newLine()
        return writer
    }

    private fun endsWithNewline(file: Path): Boolean =
        RandomAccessFile(file.toFile(), "r").use { fileHandle ->
            fileHandle.seek(fileHandle.length() - 1)
            fileHandle.read() == '\n'.code
        }

    private fun flushAll() {
        writers.values.forEach { writer -> runCatching { writer.flush() } }
    }

    private fun closeAll() {
        writers.values.forEach { writer -> runCatching { writer.close() } }
        writers.clear()
    }

    private fun purgeIfNeeded() {
        val today = LocalDate.now(clock)
        if (lastPurge == today) return
        lastPurge = today
        val cutoff = clock.millis() - retention.toMillis()
        try {
            Files.list(directory).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(SESSION_FILE_EXTENSION) }
                    .filter { Files.getLastModifiedTime(it).toMillis() < cutoff }
                    .forEach { file -> runCatching { Files.deleteIfExists(file) } }
            }
        } catch (_: IOException) {
            // Retention is best-effort: it must never stop the writer.
        }
    }
}
