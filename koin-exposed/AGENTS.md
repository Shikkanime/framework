# Module Rules: Koin-Exposed (`koin-exposed`)

This file contains specific rules for the `koin-exposed` submodule. All agents working within this
submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `koin-exposed` module bridges the `exposed` transaction model with Koin dependency injection.
It provides `applyTransactionalProxies()`, a runtime post-processor that replaces eligible Koin
singleton definitions with `TransactionalProxy`-wrapping factories so consumers never declare
`@Single(binds = ...)` proxy bindings manually.

## Submodule-Specific Rules

1. **Module Hierarchy & Dependencies**:
   - `koin-exposed` depends on `exposed` (for `@Transactional` / `TransactionalProxy`) and on the
     `koin` dependency carrier (which exposes the Koin runtime APIs). It MUST NOT depend on
     `validator`, `ktor`, `cache`, or `plugin`.

2. **Koin internals**:
   - `applyTransactionalProxies` relies on `@KoinInternalApi` registry access (mutable instances
     map, internal index key format). This is encapsulated behind the public function and tested
     against the pinned Koin version; revisit the implementation on every Koin upgrade.

3. **Consumption**:
   - Consumers call `applyTransactionalProxies()` once after `startKoin`. No per-service binding
     declaration is needed anymore. The Koin compiler plugin strict-safety flags configured in this
     module's build cover the module's own runtime-definition tests only; consumers keep whatever
     flags their own usage requires.

