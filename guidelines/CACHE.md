# Cache Guide (`cache` module)

The `cache` module provides a high-performance, two-level caching facade for the Shikkanime Framework, combining an in-memory Level 1 (L1) LRU cache with a distributed Level 2 (L2) Valkey/Redis store.

## 1. Core Architecture & Components

```text
                     Cache Facade (Cache)
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
   Level 1: L1Cache                  Level 2: ValkeyWrapper
(In-Memory LRU + TTL)             (Distributed Valkey / Redis via Glide)
             │                                 │
             └────────────────┬────────────────┘
                              ▼
                BinaryCodec (CBOR Format)
                              │
                              ▼
                SingleFlight (Deduplication)
```

- **`Cache`**: The primary entry point for caching operations. Coordinates L1 and L2 lookup, automatic serialization, version-based invalidation, and single-flight loader execution.
- **`L1Cache`**: Synchronized in-memory LRU cache based on `LinkedHashMap` with maximum capacity limits and nanosecond-precision TTL expiration.
- **`ValkeyWrapper` & `ValkeyWrapperImpl`**: Asynchronous distributed key-value store wrapper powered by AWS Glide client (`GlideClient`).
- **`BinaryCodec`**: Serializes objects to CBOR byte arrays using `kotlinx.serialization` and packs numeric versions into 8-byte buffers.
- **`SingleFlight`**: Prevents cache stampedes (thundering herd problem) by ensuring only a single loader execution runs concurrently for any given cache key.
- **`CacheStats`**: Cumulative hit, miss and put counters exposed as `cache.stats`, recorded in `Cache.loadAndStore`.

## 2. Reading & Storing Cached Items

To cache serializable objects, use `cache.get`:

```kotlin
@Serializable
data class EpisodeDto(val id: Int, val title: String)

val episode = cache.get<EpisodeDto>(
    bucket = "episodes",
    key = "episode-123",
    ttl = 1.days
) {
    // Suspending loader block executed on cache miss
    episodeRepository.findEpisodeById(123)
}
```

### Lookup Sequence:
1. **Bucket Version Lookup**: Reads the bucket version key (`ver:<bucket>`) from L1 (or L2 if absent).
2. **Key Construction**: Constructs full key `$bucket:$version:$key`.
3. **L1 Check**: If present and fresh in `L1Cache`, returns value immediately.
4. **L2 Check**: If present in Valkey, decodes CBOR byte array, populates L1, and returns value.
5. **Single-Flight Loader Execution**: On cache miss, single-flight deduplicates concurrent requests for the key, executes the loader block, serializes the result, stores it in L1 and Valkey L2, and returns the result.

## 3. Cache Invalidation & Bucket Versioning

Invalidation operates at the **bucket level** without requiring expensive wildcard key scans:

```kotlin
// Increment bucket version in Valkey and purge version key from L1
cache.invalidate("episodes")
```

When a bucket is invalidated:
1. Valkey atomically increments `ver:<bucket>` via `INCR`.
2. The local `ver:<bucket>` entry is evicted from L1.
3. Subsequent `get` calls read the new version, rendering previous cached keys obsolete while allowing old keys to expire naturally via TTL.

## 4. Best Practices & Guidelines

- **Serialization**: All cached data DTOs must be marked with `@Serializable`.
- **Granular Buckets**: Group related entities into distinct buckets (e.g. `"episodes"`, `"anime"`, `"users"`) to facilitate targeted invalidation.
- **Thread Safety**: Component implementations (`L1Cache`, `SingleFlight`, `BinaryCodec`) are thread-safe and safe for high-concurrency coroutines.
- **Mocking in Tests**: Unit tests can mock `ValkeyWrapper` or `GlideClient` to verify caching logic without a live Valkey instance.

## 5. Cache Statistics (`CacheStats`)

Every cache instance exposes cumulative hit, miss and put counters describing its effectiveness
through `cache.stats`:

```kotlin
val cache = Cache(valkeyWrapper = valkeyWrapper)

// After some traffic:
val hitRate = cache.stats.hitRate
val l1Hits = cache.stats.l1Hits
```

- **`l1Hits`**: lookups served by the in-memory L1 cache without calling Valkey or the loader.
- **`l2Hits`**: lookups served by Valkey after an L1 miss, counted only once the payload decoded.
- **`misses`**: lookups that found nothing in either level and executed the loader; recorded before
  the loader runs, so a failing loader still counts as a miss.
- **`puts`**: item entries written into one level — populating L1 from an L2 hit counts one put, a
  miss counts two puts (L1 write and Valkey write).
- **`hitRate`**: ratio of lookups served by a cache level over recorded lookups (`0.0` before any
  lookup); puts are excluded from the ratio.

Rules:

- Counters record deduplicated outcomes: concurrent gets coalesced by `SingleFlight` count once.
- Failed lookups (L2 transport error, undecodable payload) record nothing and throw to the caller.
- An L2 write failure after a successful loader keeps the recorded miss and counts only the entries
  actually written (the miss itself is factual).
- Bucket-version bookkeeping (`ver:<bucket>` lookups and writes) is never counted.
- Counters are cumulative and monotonic for the instance lifetime; there is no reset API.
- `hitRate` reads several counters in sequence, so it is a point-in-time approximation under
  concurrency.
