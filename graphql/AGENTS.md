# Module Rules: GraphQL (`graphql`)

This file contains specific rules for the `graphql` submodule. All agents working within this
submodule must strictly adhere to these guidelines in addition to the root [`AGENTS.md`](../AGENTS.md).

## Module Purpose & Scope

The `graphql` module serves the same use cases as REST over a second transport, using
`graphql-kotlin` (ExpediaGroup) on top of `graphql-java`. It owns the GraphQL engine wiring, the
schema configuration, the `/graphql` route, the security stack that guards it, and the
orchestration of consumer-provided DataLoaders.

It does **not** own business logic, repositories, or resolvers. Those belong to the consumer.

## 1. Module Hierarchy & Dependencies

- `graphql` depends on `core`, `ktor`, and the graphql-kotlin libraries.
- `graphql` **MUST NOT** depend on `exposed`. The framework has no repository and cannot know what
  to batch; a DataLoader that did would be a framework coupled to a domain that changes.
- Consumers that need DataLoaders declare a `KotlinDataLoaderRegistryFactory` instance in Koin and
  pass it to `configureGraphQL`. Nothing else.

## 2. Security Stack — non-negotiable

The endpoint must carry all four guards. Each one covers a vector the others do not:

| Guard | Covers |
|---|---|
| `RequestBodyLimit` | Oversized payload, measured on the **decoded** stream so compression cannot inflate past it |
| `Compression` | Response size; safe alongside the body limit precisely because that limit is measured after decoding |
| `DoubleReceive` | Makes the body re-readable, which `requestWeight` needs; without it every request breaks |
| `RateLimit` + `requestWeight` | Sustained abuse, charged by query cost rather than by request count |

Plus the document bounds, enforced by an interception on the route:

- `SecurityLimits.maxQueryDepth`
- `SecurityLimits.maxQueryComplexity`
- introspection exemption, when `introspectionEnabled`

**The refusal guard must stay an interception.** Registering it as a sibling `post { }` handler
lets the plugin's own handler win by registration order, which silently executes the hostile
document instead of refusing it. This was a real bug, caught by the route tests.

**`requestWeight` never charges zero, and never charges the whole bucket for a refusal.** A batch
array, invalid syntax, a query too deep for the parser, or one that breaks the bounds is charged
`RateLimitConfig.unpriceableRequestWeight` — a fixed, moderate penalty. A zero-weight request is
unlimited, which is worse than no rate limit at all; and charging the whole bucket would hand an
attacker a free denial of service, since a few hundred bytes of malformed input would lock out
every client sharing the same address for a whole refill period.

**`RateLimitConfig.limit` must exceed `maxQueryComplexity`.** Otherwise the bucket is smaller than
the complexity the bounds accept, and a query the bounds explicitly allow can never be executed: the
limiter refuses it for as long as the refill period lasts.

**`configureGraphQL`'s `rateLimitKey` defaults to the caller's address.** The module owns no
authentication, so that is the only thing it can know — and behind a shared address every client
then spends one budget. Consumers that can identify their callers should pass their own key. It is
a parameter of `configureGraphQL`, not a field of `RateLimitConfig`: a function held in a data class
becomes part of `equals` and `hashCode`, and two configurations built with equivalent lambdas would
compare unequal.

## 3. Cost Model Rules

`estimateCost` is a pure function of the document: no schema lookup, no database access. The cost
must be knowable *before* execution.

- Every field costs one unit, plus the cost of its own selections.
- A **fragment spread pays every time it appears**. A shared "already seen" set would price the
  fragment bomb at the cost of a single expansion — the opposite of what is needed.
- **The price must stay affordable to compute.** Paying for every spread is what makes the price
  attack-shaped, but a ladder of fragments where each level spreads the next one twice costs 2^k,
  which reaches billions of nodes in under a kilobyte of payload. Both walks therefore stop at a
  budget derived from the configured bounds — `SecurityLimits.costCeiling`,
  `costVisitBudget`, `depthVisitBudget`. A walk that has to be *exact* is a walk an attacker can
  make unbounded.
- The ceiling is **derived from `maxQueryComplexity`, never a hard-coded constant**. A fixed
  ceiling below a consumer's bound would saturate under it, and the hostile document would then
  pass the comparison.
- Arithmetic is **`Long`**. An unbounded `Int` sum wraps, and a wrapped zero both passes the bound
  and weighs one unit in the limiter.
- Cost is checked **before** depth, so a document refused on cost never pays for the more explosive
  of the two walks.
- The depth walk resolves spreads, so a document three levels deep cannot hide a fifty-level
  expansion.
- Cycles are tracked **per branch**, so two sibling spreads of one fragment each pay full price
  while a self-referencing fragment still terminates.
- Introspection is exempt only when the document asks for **nothing else**. A document mixing
  `__schema` and business fields is priced normally, or the exemption becomes a smuggling route.

## 4. Introspection

Public by default, matching the already-public Swagger UI. The exemption from the depth and
complexity bounds is explicit and lives in `isIntrospectionOnly`, because the introspection query
is deep and expensive by nature: lowering the threshold instead would break GraphiQL and every
codegen client.

Three rules hold that exemption, and each closes a measured bypass:

- **Only `__schema` and `__type` grant it** (`META_FIELDS`). `__typename` resolves against the
  query type, not the schema, so it carries no introspection work.
- **The document must read the schema somewhere** (`documentReadsSchema`). Otherwise
  `{ __typename }` qualifies, and so does a document aliasing it thousands of times — the cheapest
  possible bomb, free of charge. `__typename` may still *accompany* a real schema read
  (`COMPANION_META_FIELDS`), which is what GraphiQL, Apollo and Relay send.
- **A subtree may not compound through spreads.** Fields under `__schema` are bounded by the
  schema's own shape, so a fixed selection count is a fixed cost; a spread that reaches another
  spread is not. `{ __schema { types { ...F0 } } }` with a ladder of them qualified as
  introspection on name alone and skipped both bounds entirely. A spread reaching only fields is
  allowed — that is what a codegen client's `...FullType` looks like.

## 5. Error Contract

A refused document answers **HTTP 400** with a GraphQL `errors` array. GraphQL reports execution
errors with HTTP 200, but a refused document never reached the engine — it is a rejected request,
not a failed execution, and a caller branching on the status code must tell the two apart.

Never surface an internal exception message to a client.

## 6. Out of Scope for This Module

- Websocket subscriptions: the artifact ships `getSubscriptionServer()`, but the route does not
  advertise subscriptions, and `schemaSubscriptions` should stay empty unless that changes.
- Batch requests: **refused outright** with a 400, not priced. graphql-java would execute every
  element of the array as a separate operation, so a batch would answer N operations having been
  analysed as one document. The refusal is in the interception, ahead of the handler.
- Rate limiting the REST surface: the plugin is installed, but wiring it to every existing route is
  a separate change.
