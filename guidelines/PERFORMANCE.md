# Performance Guide

- **Logger Caching**: `LoggerFactory` caches `Logger` instances by class/name to avoid redundant creation overhead.
- **Reflection Caching**: Cache expensive reflection lookups (annotations, properties, method handles) when applicable.
- **Database Connection Pooling**: Ensure `HikariConfig` maximumPoolSize is tuned appropriately in `DatabaseWrapper`.
- **Lazy Iterables**: Use Exposed `SizedIterable` lazy evaluation in `AbstractRepository.findAllByIds`.
- **Object Allocations**: Minimize temporary string and DTO allocations during high-frequency HTTP routing and parameter resolution.
