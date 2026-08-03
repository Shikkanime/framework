# Database Guide (`exposed` module)

## 1. Connection & Schema Management

Database access is managed by `DatabaseWrapper`. It configures HikariCP connection pools, registers Exposed tables, initializes schemas, and applies Liquibase migrations.

```kotlin
val db = DatabaseWrapper(jdbcUrl = "...", driverClassName = "...")
db.connect()
db.addTables(UserTable)
db.initializeSchema()
db.migrateSchema("db/changelog.xml")
```

## 2. Transactions & TransactionalProxy

Operations modifying state or executing Exposed queries must run within a transaction.
- Standard transactions: `databaseWrapper.inTransaction { ... }`
- Annotation-based transactions: Mark methods with `@Transactional` and wrap interface implementations using `TransactionalProxy(databaseWrapper, target, interfaceClass).create()`.

## 3. AbstractRepository & Upsert DSL

All Exposed repositories should inherit from `AbstractRepository<ID, ENTITY>`.
It provides standard `findById`, `findAllByIds`, and a fluent upsert DSL:

```kotlin
override fun persist(id: Int?, data: String): MyEntity {
    return findById(id)
        .ifExists {
            // Updated if entity exists
            this.someProperty = data
        }
        .newIfNotExists(id) {
            // Initialized if entity was null
            this.someProperty = data
        }
        .applyFlush() // Flushes changes to database
}
```

- **`.ifExists { ... }`**: Safe call executed if non-null.
- **`.newIfNotExists(id) { ... }`**: Executed if the preceding reference was null.
- **`.applyFlush()`**: Commits entity state to DB and returns the entity.
