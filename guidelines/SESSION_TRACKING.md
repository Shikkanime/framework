# Session Tracking

The `ktor` module ships a persistent session journal, enabled by default for every project using
`configureDefaultModules()`. It answers one question: what happened in one user session, including
across a restart and across services.

## Identifiers

- `X-Session-Id` — long-lived session identifier. A client generates it or keeps the one returned
  by the first response. It is a correlation token, **never** an authorization credential.
- `X-Request-Id` — identifier of one request. It is generated when absent, echoed on the response,
  and the parent link of every outgoing call.
- `X-Parent-Request-Id` — set by `createHttpClient()` on an outgoing call; the called service
  records it on its `IN` event.

Only `[A-Za-z0-9_-]{8,64}` is accepted. A malformed value is replaced by a generated identifier
instead of failing the request, and a header value never becomes a file name without validation.

## Storage

`FileSessionStore` writes one append-only JSONL file per session and per service:

```
<SHIKKANIME_LOGS_DIR>/sessions/<service>/<sessionId>.jsonl
```

Default directory: `$SHIKKANIME_LOGS_DIR/sessions`, or `<working directory>/logs/sessions`.
Default service: `MODULE_NAME`, then `SHIKKANIME_SERVICE`, then `default`.

Events are `IN` (request received), `OUT` (request sent by `createHttpClient`) and `EVENT`
(application-defined). They carry status, duration, path without query string, target host, error
type/message and response size. Bodies, query strings and personal identifiers are never stored.

Retention deletes a file 7 days after its last write by default; configure `SessionConfig.retention`.
A partial last line (crash) is ignored on read and repaired on the next append.

## Performance contract

- `SessionStore.append` is called on the request thread and must not block. `FileSessionStore`
  offers the event to a bounded `ArrayBlockingQueue` and returns immediately.
- A single daemon thread serializes and writes the events, flushes when the queue drains, and runs
  retention once a day. A full queue drops the event (`droppedEvents`) instead of slowing a request.
- Reads are bounded by `limit` and run on `Dispatchers.IO`; the trace endpoint is not on the request
  path.
- The writer thread runs at minimum priority. The journal is a diagnostic side channel, not part of
  the response path.

## Read API

`GET /v1/session-traces/{sessionId}` and `GET /v1/session-traces` are registered only when
`SHIKKANIME_SESSION_TOKEN` (or `SessionConfig.apiToken`) is set. Both require
`Authorization: Bearer <token>`. An unknown session is 404, an invalid identifier 400, a missing or
wrong token 401. The endpoints are excluded from tracking.

## Configuration and extension

```kotlin
configureDefaultModules(
    session = SessionConfig(
        enabled = true,
        service = "api",
        retention = Duration.ofDays(7),
        apiToken = System.getenv("SHIKKANIME_SESSION_TOKEN")
    )
)
```

A project that needs a database implements `SessionStore` (the implementation can live in a module
that depends on Exposed) and passes it through `SessionConfig.store`. The framework then leaves the
store lifecycle to the caller; the `ktor` module keeps no dependency on `exposed` or `cache`.

`SessionContext.current()` exposes the current scope to a controller or service,
`SessionContext.event` appends an application-defined event, and `SessionContext.with` opens a
scope around a background job.

## Out of scope

Log correlation (session identifiers in every log line) is a separate layer. The journal is the
timeline; the log files are the detail.
