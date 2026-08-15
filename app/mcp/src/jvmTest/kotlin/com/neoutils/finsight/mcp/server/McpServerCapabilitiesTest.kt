package com.neoutils.finsight.mcp.server

import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.neoutils.finsight.mcp.server.DeclaredClientName

class McpServerCapabilitiesTest {

    @Test
    fun `the revision this server speaks is 2025-11-25`() {
        assertEquals("2025-11-25", TARGET_PROTOCOL_VERSION)
        assertTrue(TARGET_PROTOCOL_VERSION in SUPPORTED_PROTOCOL_VERSIONS)
    }

    @Test
    fun `the tool list change notification is declared`() {
        // The announced listing is not constant — the permission level decides it — so a client
        // has to be told when it changes.
        assertEquals(true, finsightServerCapabilities().tools?.listChanged)
    }

    @Test
    fun `nothing the next revision deprecates is offered`() {
        val capabilities = finsightServerCapabilities()

        // Logging is a server capability, and its absence is the whole enforcement.
        assertNull(capabilities.logging)
        // Roots and Sampling are capabilities a client declares; a server adopts them by calling
        // listRoots and createMessage. This server declares neither an experimental stand-in nor
        // an extension for them.
        assertNull(capabilities.experimental)
        assertNull(capabilities.extensions)
        assertNull(capabilities.tasks)
    }

    @Test
    fun `the server names itself for the client that lists it`() {
        assertEquals("finsight", FINSIGHT_SERVER_INFO.name)
        assertTrue(FINSIGHT_SERVER_INFO.version.isNotBlank())
    }

    @Test
    fun `nobody has introduced themselves until a client does`() {
        // Null is "no declaration yet", never a failure: a connection can be dropped and resumed
        // without the client repeating who it is.
        assertNull(DeclaredClient(DeclaredClientName()).name)
    }
}
