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
