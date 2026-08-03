# Security Guide

- **Validate all inputs**: Controller inputs must be validated using `@Valid` and `Validator`.
- **Sanitize Exception Output**: Never return raw exception tracebacks or SQL syntax errors to clients. Convert errors to `ErrorMessageDto`.
- **Log Hygiene**: `LoggerFactory` must never format or log passwords, authentication tokens, API keys, or personal identifiers.
- **Reflection Boundaries**: In `validator` and `ktor`, invoke reflection safely (`KProperty.call`, `Method.invoke`) handling potential access or invocation exceptions gracefully.
