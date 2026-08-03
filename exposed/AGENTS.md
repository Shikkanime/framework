# Module Rules: Exposed (`exposed`)

This file contains specific rules for the `exposed` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `exposed` module handles database persistence, connection pooling (`HikariCP`), schema migrations (`Liquibase`), Exposed ORM integration, transaction management (`@Transactional` & `TransactionalProxy`), and repository base classes (`AbstractRepository`).

## Submodule-Specific Rules (Règles du sous-module)

1. **Module Hierarchy & Dependencies**:
   - `exposed` depends on `core`. It MUST NOT depend on `validator` or `ktor`.

2. **Database Connection & Schema Management (`DatabaseWrapper`)**:
   - `DatabaseWrapper` coordinates HikariCP configuration, Exposed connection registration, and Liquibase migrations.
   - Schema modifications (`initializeSchema`, `dropSchema`, `migrateSchema`) must be protected by Exposed's database lock (`withDataBaseLock`).

3. **Transaction Propagation & Proxies**:
   - Transactions use `ThreadLocal<JdbcTransaction>` to support nested transaction re-use on the same thread.
   - `TransactionalProxy` creates JDK dynamic proxies intercepting methods annotated with `@Transactional` (on interface or implementation).
   - Ensure proxied target instances implement explicit interfaces.

4. **AbstractRepository & Upsert DSL**:
   - Repositories managing Exposed DAO entities must extend `AbstractRepository<T, ENTITY>`.
   - Always use the upsert DSL (`ifExists`, `newIfNotExists`, `applyFlush`) for entity persistence and updates:
     ```kotlin
     findById(id)
         .ifExists { /* update fields */ }
         .newIfNotExists(id) { /* set initial fields */ }
         .applyFlush()
     ```
   - Prefer `findAllByIds` returning `SizedIterable` for batch retrieval.
