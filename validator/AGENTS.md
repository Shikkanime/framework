# Module Rules: Validator (`validator`)

This file contains specific rules for the `validator` submodule. All agents working within this submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope
The `validator` module provides a lightweight, reflection-based validation engine (`Validator`), standard validation annotations (`@RequireAtLeastOneValid`, `@NotNull`, `@NotBlank`, `@NotEmpty`), and the `ObjectNotValidException` error.

## Submodule-Specific Rules (Règles du sous-module)

1. **Module Hierarchy & Dependencies**:
   - `validator` requires Kotlin reflection (`kotlin-reflect`). It MUST NOT depend on `exposed` or `ktor`.

2. **Validation Engine Behavior (`Validator`)**:
   - `Validator.validate(obj)` inspects member properties via Kotlin reflection (`obj::class.memberProperties`).
   - Errors MUST be aggregated across all properties before throwing `ObjectNotValidException(errors.joinToString("; "))`. Never fail-fast on the first error.

3. **Annotation Semantics & Combinations**:
   - `@NotNull`: Triggers error when property value is `null`.
   - `@NotBlank`: Applies only when the value is a `CharSequence`. Empty or blank strings trigger errors. Null values are ignored (combine with `@NotNull` if nulls are prohibited).
   - `@NotEmpty`: Applies only when the value is a `Collection`. Empty collections trigger errors. Null values are ignored (combine with `@NotNull` if nulls are prohibited).
   - `@RequireAtLeastOneValid`: Class-level annotation. Fails if no non-null member property is free of validation errors.

4. **Reflection Safety & Error Handling**:
   - Ensure property evaluation via `property.call(obj)` does not cause unexpected side effects or unhandled exceptions.
