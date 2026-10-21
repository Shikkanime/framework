package fr.shikkanime.cache

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe counters describing the effectiveness of a [Cache].
 *
 * Every lookup served by [Cache.get] ends in exactly one recorded outcome: an L1 hit ([l1Hits]),
 * an L2 hit ([l2Hits]) or a miss ([misses]). [puts] counts item entries written into one cache
 * level. Gets coalesced by the single-flight deduplication of [Cache] record one outcome for the
 * whole group, and a failed lookup (lookup-phase transport or decode error) records nothing
 * because it never produced an outcome. Bucket-version bookkeeping is never counted.
 *
 * Counters are cumulative and monotonically increasing for the lifetime of the owning [Cache];
 * there is no reset API. Recording is internal to the `cache` module, so consumers only read the
 * counters through this class.
 *
 * @see Cache.stats
 */
class CacheStats {
    private val l1HitCounter = AtomicLong()
    private val l2HitCounter = AtomicLong()
    private val missCounter = AtomicLong()
    private val putCounter = AtomicLong()

    /**
     * Number of lookups served by the in-memory L1 cache without calling Valkey or the loader.
     *
     * @return the cumulative count of L1 hits.
     */
    val l1Hits: Long get() =
        l1HitCounter.get()

    /**
     * Number of lookups served by the distributed L2 Valkey cache after an L1 miss.
     *
     * An L2 hit is recorded only once the stored payload has been decoded successfully, so a
     * corrupt payload never counts as a hit.
     *
     * @return the cumulative count of L2 hits.
     */
    val l2Hits: Long get() =
        l2HitCounter.get()

    /**
     * Number of lookups that found nothing in either level and executed the loader.
     *
     * The miss is recorded before the loader runs, so a failing loader still counts as a miss.
     *
     * @return the cumulative count of misses.
     */
    val misses: Long get() =
        missCounter.get()

    /**
     * Number of item entries written into one cache level.
     *
     * Populating L1 from an L2 hit counts one put; a miss counts two puts (L1 write and Valkey
     * write). Bucket-version bookkeeping writes are not item entries and are never counted.
     *
     * @return the cumulative count of puts.
     */
    val puts: Long get() =
        putCounter.get()

    /**
     * Ratio of lookups served by a cache level over all recorded lookups.
     *
     * Each counter is read exactly once, so the ratio always stays within its range; the snapshot
     * is still a point-in-time approximation under concurrency. Puts are excluded from the ratio.
     *
     * @return the hit ratio between `0.0` and `1.0`, or `0.0` when no lookup is recorded yet.
     */
    val hitRate: Double get() {
        val l1Count = l1Hits
        val l2Count = l2Hits
        val totalLookups = l1Count + l2Count + misses
        return if (totalLookups == 0L) 0.0 else (l1Count + l2Count).toDouble() / totalLookups
    }

    /**
     * Records a lookup served by the in-memory L1 cache.
     *
     * @return the L1 hit count after the increment.
     */
    internal fun recordL1Hit(): Long =
        l1HitCounter.incrementAndGet()

    /**
     * Records a lookup served by the distributed L2 Valkey cache after a successful decode.
     *
     * @return the L2 hit count after the increment.
     */
    internal fun recordL2Hit(): Long =
        l2HitCounter.incrementAndGet()

    /**
     * Records a lookup that found nothing in L1 nor L2 and falls back to the loader.
     *
     * @return the miss count after the increment.
     */
    internal fun recordMiss(): Long =
        missCounter.incrementAndGet()

    /**
     * Records one item entry written into one cache level (L1 or L2).
     *
     * @return the put count after the increment.
     */
    internal fun recordPut(): Long =
        putCounter.incrementAndGet()
}
