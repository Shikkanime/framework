# Code Style Guide

## General Style

- **Write all code, examples, KDoc, and comments in English.**
- **Prefer short, readable, direct code** with explicit, descriptive variable and method names.
- **Maintain single responsibility** for classes and functions.
- **Keep comments focused** on business logic intent or non-obvious technical requirements.
- **Format code consistently** adhering to Kotlin standard style guidelines.
- **Reuse existing project patterns** (e.g. factory patterns, annotations, proxy handlers).

## Imports

- **Always import symbols directly** (`import kotlinx.serialization.ExperimentalSerializationApi`); never reference a type by its fully-qualified name inline (e.g. `kotlinx.serialization.ExperimentalSerializationApi::class`). This keeps the code short and readable.

## Expression Bodies

- For expression-body functions, put the `=` at the end of the signature line and the body expression on the **next line**, indented one level:

```kotlin
@GetMapping("/ok")
fun ok(): ResponseEntity<Book> =
    ResponseEntity.ok(Book(1, "Dune"))
```

## Comments & Intent

When adding internal framework handlers or reflection logic, document the technical rationale:

```kotlin
// Retrieve target implementation method to check for @Transactional annotation
val targetMethod = target.javaClass.getMethod(method.name, *method.parameterTypes)
```
