package fr.shikkanime.ktor.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

private const val DEFAULT_QUEUE_CAPACITY = 16_384
private const val LAST_LINE_WINDOW_BYTES = 4_096L
private const val FIRST_LINE_ATTEMPTS = 3

/**
 * Session store writing one append-only JSONL file per session and per service.
 *
 * Files live under `<directory>/<service>/<sessionId>.jsonl`. Writes go through [SessionWriter],
 * which keeps them off the request thread; reads are bounded by the requested limit and happen on
 * [Dispatchers.IO]. This is the default store used by `configureDefaultModules`.
 *
 * @param directory root directory of the session journals.
 * @param service service name used as the subdirectory.
 * @param retention how long an inactive session file is kept.
 * @param queueCapacity maximum number of events waiting for the writer.
 * @param clock clock used for retention.
 */
class FileSessionStore(
    directory: Path,
    private val service: String,
    retention: Duration = Duration.ofDays(7),
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    clock: Clock = Clock.systemUTC()
) : SessionStore {
    private val serviceDirectory: Path = directory.resolve(service)
    private val writer: SessionWriter

    /**
     * Number of events dropped because the writer queue was full.
     *
     * A non-zero value means the service produced events faster than the single writer could drain
     * them; the request path itself was never blocked.
     */
    val droppedEvents: Long
        get() = writer.droppedEvents()

    init {
        require(SessionIds.isValidService(service)) { "Invalid session service name: $service" }
        require(queueCapacity > 0) { "The session queue capacity must be positive" }
        Files.createDirectories(serviceDirectory)
        writer = SessionWriter(serviceDirectory, retention, queueCapacity, clock)
    }

    override fun append(event: SessionEvent) =
        writer.append(event)

    override suspend fun find(sessionId: String, limit: Int): SessionTrace? =
        withContext(Dispatchers.IO) { readTrace(sessionId, limit) }

    override suspend fun sessions(limit: Int): List<SessionSummary> =
        withContext(Dispatchers.IO) { readSummaries(limit) }

    override fun flush() =
        writer.flush()

    override fun close() =
        writer.close()

    private fun readTrace(sessionId: String, limit: Int): SessionTrace? {
        if (!SessionIds.isValid(sessionId)) return null
        require(limit > 0) { "The session event limit must be positive" }
        val file = serviceDirectory.resolve(sessionId + SESSION_FILE_EXTENSION)
        if (!Files.isRegularFile(file)) return null
        val events = ArrayDeque<SessionEvent>(limit)
        var first: String? = null
        var last: String? = null
        var count = 0
        Files.newBufferedReader(file, UTF_8).useLines { lines ->
            lines.forEach { line ->
                val event = decode(line) ?: return@forEach
                if (first == null) first = event.timestamp
                last = event.timestamp
                count++
                if (events.size == limit) events.removeFirst()
                events.addLast(event)
            }
        }
        val firstSeen = first ?: return null
        val lastSeen = last ?: return null
        return SessionTrace(
            sessionId = sessionId,
            service = service,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            eventCount = count,
            truncated = count > limit,
            events = events.toList()
        )
    }

    private fun readSummaries(limit: Int): List<SessionSummary> {
        require(limit > 0) { "The session summary limit must be positive" }
        val files = Files.list(serviceDirectory).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(SESSION_FILE_EXTENSION) }
                .sorted(
                    Comparator
                        .comparing<Path, Long> { file -> Files.getLastModifiedTime(file).toMillis() }
                        .reversed()
                )
                .limit(limit.toLong())
                .toList()
        }
        return files.map(::summary)
    }

    private fun summary(file: Path): SessionSummary =
        SessionSummary(
            sessionId = file.fileName.toString().removeSuffix(SESSION_FILE_EXTENSION),
            service = service,
            firstSeen = readFirstEvent(file)?.timestamp,
            lastSeen = readLastLine(file)?.let(::decode)?.timestamp,
            sizeBytes = Files.size(file)
        )

    private fun readFirstEvent(file: Path): SessionEvent? =
        Files.newBufferedReader(file, UTF_8).use { reader ->
            repeat(FIRST_LINE_ATTEMPTS) {
                val line = reader.readLine() ?: return null
                decode(line)?.let { return it }
            }
            null
        }

    private fun readLastLine(file: Path): String? {
        RandomAccessFile(file.toFile(), "r").use { fileHandle ->
            var end = fileHandle.length()
            if (end == 0L) return null
            var window = LAST_LINE_WINDOW_BYTES
            while (end > 0) {
                val start = maxOf(0L, end - window)
                val bytes = ByteArray((end - start).toInt())
                fileHandle.seek(start)
                fileHandle.readFully(bytes)
                val text = String(bytes, UTF_8)
                val newline = text.lastIndexOf('\n', text.length - 2)
                if (newline >= 0) {
                    val line = text.substring(newline + 1).trim()
                    if (line.isNotEmpty()) return line
                    end = start + newline
                    window = LAST_LINE_WINDOW_BYTES
                } else if (start == 0L) {
                    return text.trim().takeIf { it.isNotEmpty() }
                } else {
                    end = start
                    window *= 2
                }
            }
        }
        return null
    }

    private fun decode(line: String): SessionEvent? =
        try {
            SESSION_JSON.decodeFromString<SessionEvent>(line)
        } catch (_: Exception) {
            null
        }
}
