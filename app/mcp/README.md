# `:app:mcp` — the MCP server

An MCP server that runs **inside the desktop app's process**, over the same Koin graph and the
same Room database the window is using. It listens on loopback only, it is off until the user
turns it on, and every write it performs goes through a use case the domain already owns.

It is an `impl` without a screen: it may depend on any `feature:*:api` and on `:core:*`, and on
no `feature:*:impl`. The convention plugin `finsight.app.mcp` enforces exactly that.

---

## 1. The protocol revision, and the debt

**This server speaks revision `2025-11-25`.** Not because that is the current revision of the
Model Context Protocol — it is not — but because it is the newest revision the Kotlin MCP SDK
speaks: `kotlin-sdk` 0.15.0 declares `LATEST_PROTOCOL_VERSION = "2025-11-25"`, and
`TARGET_PROTOCOL_VERSION` in `McpServerCapabilities.kt` reads it from there rather than repeating
it.

The revision after it, **`2026-07-28`**, exists. It is stateless: it removes the handshake and
sessions, requires `server/discover`, replaces server-initiated requests, and inverts the
cancellation rule. No JVM SDK implements it — the Kotlin SDK has an open issue tracking it, and
the Java SDK is a release behind that. The two ways out were writing the transport by hand against
the new revision, or speaking `2025-11-25` and using the SDK. **The second was chosen**: the
transport is not the product, and hand-maintaining one does not pay for itself on a single-user
local server.

### The debt, dated

| | |
|---|---|
| **What** | The server lags one revision behind the protocol. |
| **Since** | The change `mcp-server-desktop`, targeting `2025-11-25`. |
| **Trigger** | **The Kotlin MCP SDK publishing support for `2026-07-28`.** Objective, and checkable by reading `LATEST_PROTOCOL_VERSION` in the SDK on the next dependency bump. |
| **Cost of waiting** | Bounded on purpose: see below. |

**The design avoids accumulating what the migration would have to undo.**

- **No Roots, no Sampling, no Logging** — all three are deprecated by the next revision. Logging
  is a server capability, and `finsightServerCapabilities()` leaves it null; Roots and Sampling
  are client capabilities a server adopts by calling `listRoots` and `createMessage`, and this
  server calls neither.
- **No session.** The revision permits sessions; the next one removes them. The SDK's session
  generator is switched off, so no `Mcp-Session-Id` is ever emitted and no request has to carry
  one. It is also one fewer secret to leak.
- **Cancellation is the one inversion that matters.** Here, **losing the connection is not a
  cancellation** — the client cancels by sending `notifications/cancelled`, and nothing else
  cancels. In `2026-07-28` closing the stream *is* cancelling. `CancellationHandling.kt` states
  the rule and is the first place to revisit when the migration happens.
- **The idempotency key becomes a prerequisite.** In the next revision, a server that asks the
  client for input has the *client re-run the original request* — the tool call runs twice.
  Deriving the key from a digest of the arguments from the start is what keeps that migration
  cheap.

---

## 2. The transport

Streamable HTTP, on `127.0.0.1` and nothing else — never every interface, not even behind a
setting. **One endpoint path**, `/mcp`: `POST` carries the requests, `GET` opens the stream of
notifications the server initiates. There is no third path and no second verb.

**`Origin` is validated on every request, first.** Present and unrecognised is `403`, decided in
the pipeline before any tool runs and before a row is read. It is a `MUST` of the revision, and
the reason is concrete: without it a web page the user has open reaches this server by DNS
rebinding, and this server writes to the ledger. A request with no `Origin` at all is admitted —
a non-browser client cannot be rebound.

**The protocol version header is required**, and a value outside the supported set is `400`. One
exception, which the revision itself defines: the `initialize` request cannot carry a negotiated
version, since negotiating it is what that request is for. This server reads that exception
literally and uses it as a signal — **a `POST` without the header is an initialisation** — which
is also what lets a client that restarted connect again.

**One MCP connection is shared by every HTTP exchange**, and it has to be: a cancellation arrives
as a *separate* request, and it can only find the call it names if both landed on the same
connection. The shared connection is rebuilt on the next initialisation and at no other time; in
particular an HTTP exchange ending never closes it, which is what keeps a dropped connection from
behaving like a cancellation.

**Tool invocation is rate limited** — a `MUST` of the revision. The refusal is named
(`RATE_LIMITED`) and repeatable, and it is `UNAVAILABLE`, so it is told apart from a refusal by a
rule of the domain **by class**: that one is `DOMAIN_RULE` and is never retryable. A refused call
performs no write, because the limiter is consulted before the tool is reached.

---

## 3. Authorization — a declared deviation

Every request presents a **static bearer token in the authorization header**. That is a
**deliberate deviation** from the MCP authorization specification, recorded here rather than
implied.

That specification is optional, and conforming to it is recommended for HTTP transports: within
it, this server would be an OAuth 2.1 resource server, with a protected resource metadata document
and tokens minted by an authorization server. Standing an OAuth 2.1 deployment up for a
single-user loopback server is disproportionate to what it protects, so it was not done.

What survives from the specification is the part that makes a failure legible:

- A request without a token, or with the wrong one, is **`401` with an authorization challenge**
  naming the scheme, the realm and `resource_metadata`.
- **The document that points to is actually served**, unauthenticated, at
  `/.well-known/oauth-protected-resource`. A document describing which credential to bring, placed
  behind that credential, would say nothing to the client that needs it.

Three properties are not negotiable, and each is a `MUST` of the access-control specification:

- **The token never travels in a query string.** A request carrying a credential there is refused
  *and* the token is rotated: a secret that reached a query string is already in somebody's shell
  history, and refusing without revoking would leave the leak in force.
- **The comparison is constant time**, via `:core:common`'s `constantTimeEquals` — the same owner
  as the generator on the other side of the comparison.
- **The token is never logged, never sent to telemetry, and never written to the activity
  journal.** No refusal message and no challenge ever repeats it.

---

## 4. The lifecycle

`McpServerController` is `expect` in `commonMain`, **actual on the JVM only**; Android and iOS get
inert implementations that never listen. The server is a desktop capability because the invariant
that matters is a **single owner of the database**: the client reaches a process that is already
running and already owns `~/.finance/finsight.db`. Two Room instances over one file would have
independent invalidation trackers, and the window would go on showing a stale balance after the
agent wrote — a finance app lying about the balance because of the agent is worse than no agent.

It follows two independent keys of `IMcpServerSettingsRepository`:

- **the toggle**, which decides whether a socket exists at all. Off means **nothing listening** —
  not an idle socket, not a closed one;
- **the permission level**, which decides which tools are announced. Changing it at runtime emits
  the tool list change notice, so a connected client stops seeing a listing that is no longer
  true.

Hiding a write tool is for the well-behaved client; **refusing it is what holds**. Every tool is
registered with the protocol, announced or not, so a client that ignores the annotations and calls
a write at read-only level is refused *by permission* — `PERMISSION_READ_ONLY` — and not told the
tool does not exist, which would send it looking for a spelling mistake.

**A port that is taken is not a port that moves.** The persisted port is reused on every start; if
another process holds it, the server does not start, and the conflict is published as a state of
its own for the screen to name. Choosing another port silently is the same breakage as an ephemeral
port — only rarer, and therefore harder to diagnose.

**Nothing of the Finsight interface is involved.** The server is not started from a composition
scope, it reads no window state, and no tool needs anyone looking at the app to finish: a tool
completes with the window minimised. Consent is the *policy* — what Settings permits — never a
modal. The protocol's own way of asking the user something happens in the **client's** interface,
and that stays available.

---

## 5. Map

| File | What it owns |
|---|---|
| `contract/` | The shape of every answer: money by currency, the outcome envelope, pagination, the assumed defaults, the tool registry. Common code. |
| `transport/McpHttpTransport.kt` | The endpoint, the `Origin` check, the version header, the absence of sessions. |
| `transport/BearerTokenAuth.kt` | The credential, the challenge, and the declared deviation. |
| `transport/ToolRateLimiter.kt` | The named, repeatable refusal that bounds a runaway loop. |
| `server/McpServerCapabilities.kt` | The revision, what is offered, what is deliberately not, and the client's self-declared name. |
| `server/CancellationHandling.kt` | Which interruptions are cancellations, and what is emitted after one. |
| `McpServerController.kt` / `.jvm.kt` | Start, stop, follow the configuration, publish the state. |
