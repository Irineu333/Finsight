package com.neoutils.finsight.mcp.server

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the connected client called itself when it initialised the connection — the value the
 * activity journal records as `client`.
 *
 * **Self-declared, not authenticated.** It says who claimed to be calling, never who was: what
 * authenticates a request is the bearer token, and that token is the same for every client.
 * Whatever renders this must not present it as a verified fact.
 *
 * It is one object shared by the two halves that cannot see each other: the transport, which
 * learns the name at `initialize`, and the activity recorder, which writes it. Without a shared
 * holder the recorder has nothing to read and every record is written with no client at all —
 * which is what happened before this existed, silently, because a nullable field looks the same
 * whether the client did not introduce itself or nobody ever asked.
 *
 * `null` means "nobody has introduced themselves yet", and that is a defined state, not a
 * failure: a connection can be dropped and resumed without the declaration being repeated.
 */
class DeclaredClientName {

    private val declared = MutableStateFlow<String?>(null)

    /** The last name a client declared, or `null` when none ever did. */
    val name: String? get() = declared.value

    /** Records a declaration. Blank is the same as none, and never overwrites a real name. */
    fun declare(name: String?) {
        name?.takeIf { it.isNotBlank() }?.let { declared.value = it }
    }
}
