package com.neoutils.finsight.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import java.util.concurrent.atomic.AtomicReference

/**
 * The revision of the Model Context Protocol this server speaks: **`2025-11-25`**.
 *
 * It is read from the SDK rather than written down, because the reason it is the target is that
 * the SDK speaks it: `LATEST_PROTOCOL_VERSION` in `kotlin-sdk` 0.15.0. The revision after it,
 * `2026-07-28`, exists and is not implemented by any JVM SDK; targeting it would mean writing the
 * transport by hand, which was refused as disproportionate for a single-user loopback server.
 *
 * **That lag is dated debt with an objective trigger — the SDK speaking `2026-07-28` — and it is
 * written down in `app/mcp/README.md`.** A lag chosen and recorded is a decision; the same lag
 * unrecorded is a surprise for whoever maintains this next.
 */
const val TARGET_PROTOCOL_VERSION: String = LATEST_PROTOCOL_VERSION

/** What this server calls itself when a client initialises the connection. */
val FINSIGHT_SERVER_INFO: Implementation = Implementation(
    name = "finsight",
    version = "1",
    title = "Finsight",
)

/**
 * What this server declares it offers, and — just as deliberately — what it does not.
 *
 * **Tool list change notification is declared**, because the announced tool list is not constant:
 * the permission level decides which tools are announced, and changing it at runtime has to reach
 * a connected client. Without this capability a client that switched to read and write would go
 * on seeing the read-only listing until it reconnected.
 *
 * **Logging is absent, and so are Roots and Sampling.** The next revision deprecates all three,
 * and the point of choosing a lagging revision knowingly is not to accumulate what the migration
 * would have to undo. Roots and Sampling are capabilities a *client* declares; a server adopts
 * them by calling `listRoots` and `createMessage`, and this server calls neither. Logging is a
 * server capability, and it is left null here — the field is the whole enforcement.
 *
 * Resources and prompts are declared because the surface includes both; each is announced with
 * its own list change notification for the same reason tools are.
 */
fun finsightServerCapabilities(): ServerCapabilities = ServerCapabilities(
    tools = ServerCapabilities.Tools(listChanged = true),
    resources = ServerCapabilities.Resources(listChanged = true, subscribe = false),
    prompts = ServerCapabilities.Prompts(listChanged = true),
    logging = null,
    completions = null,
    tasks = null,
    experimental = null,
    extensions = null,
)

/**
 * The name the client declared about itself when it initialised the connection.
 *
 * **Self-declared, not authenticated.** It says who claimed to be calling, never who was: what
 * authenticates a request is the bearer token, and that token is the same for every client.
 * Whatever renders this — the activity journal's `client` field, which this feeds — must not
 * present it as a verified fact.
 *
 * It is captured once per initialisation and kept afterwards, because a connection can be dropped
 * and resumed without the declaration being repeated; the field it feeds is nullable for exactly
 * that reason, and `null` here is "nobody has introduced themselves yet", not a failure.
 */
class DeclaredClient(private val declared: DeclaredClientName) {

    /** The last name a client declared, or `null` when none ever did. */
    val name: String? get() = declared.name

    /**
     * Watches [session] for its initialisation and records what the client called itself.
     *
     * The SDK stores the client's `Implementation` while it answers `initialize`, and fires the
     * callback registered here when the client's `notifications/initialized` follows, so the name
     * is already there by the time this reads it.
     */
    fun observe(session: ServerSession) {
        session.onInitialized {
            declared.declare(session.clientVersion?.name)
        }
    }
}
