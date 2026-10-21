package fr.shikkanime.cache

import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Asserts the four cumulative counters of a [CacheStats] instance against the expected values.
 *
 * Every counter defaults to `0`, so a call states only the outcomes it expects and the remaining
 * counters are asserted to be untouched.
 *
 * @param stats the statistics instance under test.
 * @param l1Hits the expected L1 hit count.
 * @param l2Hits the expected L2 hit count.
 * @param misses the expected miss count.
 * @param puts the expected put count.
 */
internal fun assertCounters(
    stats: CacheStats,
    l1Hits: Long = 0,
    l2Hits: Long = 0,
    misses: Long = 0,
    puts: Long = 0
) {
    assertEquals(l1Hits, stats.l1Hits)
    assertEquals(l2Hits, stats.l2Hits)
    assertEquals(misses, stats.misses)
    assertEquals(puts, stats.puts)
}
