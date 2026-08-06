# Module Rules: Cache (`cache`)

This file contains specific rules for the `cache` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `cache` module provides a two-level caching engine combining an in-memory L1 LRU cache (`L1Cache`), a distributed L2 Valkey/Redis client wrapper (`ValkeyWrapper`), CBOR binary serialization (`BinaryCodec`), bucket versioning, and single-flight loader deduplication (`SingleFlight`).

## Submodule-Specific Rules (Règles du sous-module)

1. **Module Hierarchy & Dependencies**:
   - `cache` depends on `core`. It MUST NOT depend on `exposed`, `validator`, or `ktor`.
   - Third-party dependencies must remain scoped to `serializationEcosystem`, `valkeyGlide`, and `kotlinxCoroutinesCore`.

2. **Two-Level Caching Architecture**:
   - `L1Cache` handles thread-safe, in-memory LRU storage with nanoTime-based TTL checks.
   - `ValkeyWrapper` handles asynchronous distributed storage in Valkey/Redis using AWS Glide (`GlideClient`).
   - Read flow: L1 lookup -> L2 Valkey lookup -> loader execution -> update L1 & L2.

3. **Bucket Versioning & Invalidation**:
   - Bucket invalidation MUST use atomic version increments (`incr("ver:$bucket")`) and L1 version eviction (`remove("ver:$bucket")`).
   - Do NOT execute wildcard scan/delete commands in Valkey for bucket invalidation.

4. **Single-Flight Loader Execution**:
   - Concurrent reads for identical full keys MUST be deduplicated using `SingleFlight.doOnce(key) { loader() }`.
   - Ensure loader exceptions are completed exceptionally on `CompletableDeferred` to prevent hanging coroutines.

5. **Binary Codec & Serialization**:
   - Payload serialization uses `BinaryCodec` with Kotlinx Serialization CBOR format.
   - Version values are binary-encoded as 8-byte `Long` values using `ByteBuffer`.
