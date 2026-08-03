# Kotlin Conventions

- **Use explicit return types** on all public framework functions and API methods.
- **Use expression bodies (`=`)** for single-line functions.
- **Prefer immutability**: use `val`, read-only collections (`List`, `Map`), and `data class`.
- **Add KDoc comments** to all public framework classes, interfaces, annotations, and functions to document behavior and rules.
- **Leverage Kotlin features safely**: use null safety features (`?.`, `?:`, `let`), inline functions where appropriate, and reflection extension functions cleanly.

```kotlin
fun getLogger(clazz: Class<*>): Logger =
    map.getOrPut(clazz.name) { buildLogger(clazz.name) }
```
