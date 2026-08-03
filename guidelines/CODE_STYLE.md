# Code Style Guide

## General Style

- **Write all code, examples, KDoc, and comments in English.**
- **Prefer short, readable, direct code** with explicit, descriptive variable and method names.
- **Maintain single responsibility** for classes and functions.
- **Keep comments focused** on business logic intent or non-obvious technical requirements.
- **Format code consistently** adhering to Kotlin standard style guidelines.
- **Reuse existing project patterns** (e.g. factory patterns, annotations, proxy handlers).

## Comments & Intent

When adding internal framework handlers or reflection logic, document the technical rationale:

```kotlin
// Retrieve target implementation method to check for @Transactional annotation
val targetMethod = target.javaClass.getMethod(method.name, *method.parameterTypes)
```
