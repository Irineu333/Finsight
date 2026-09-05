package com.neoutils.finsight.feature.mcp.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **What the app is allowed to ask a socket for.**
 *
 * The range is the contract, and every surface that collects a port derives what it accepts from
 * it, so the one thing worth pinning is the range itself. The privileged ports are out: the app runs
 * as the user, the bind is refused before the server exists, and the platform reports that refusal
 * as the very exception a port already held produces — so a port offered here and not takeable would
 * reach the user as a clash with a program that is not running.
 */
class McpServerPortRangeTest {

    @Test
    fun `no privileged port is offered`() {
        assertEquals(
            1024,
            McpServerController.VALID_PORTS.first,
            "The range reaches below 1024, where an unprivileged process is refused the bind.",
        )
        assertFalse(
            80 in McpServerController.VALID_PORTS,
            "A port the app cannot take is offered, and the refusal would read as a busy port.",
        )
        assertFalse(
            443 in McpServerController.VALID_PORTS,
            "A port the app cannot take is offered, and the refusal would read as a busy port.",
        )
    }

    @Test
    fun `the range ends where port numbers end`() {
        assertEquals(65535, McpServerController.VALID_PORTS.last)
        assertFalse(65536 in McpServerController.VALID_PORTS, "65536 is not a port.")
    }

    @Test
    fun `the port the server binds unless it is moved is one the range offers`() {
        assertTrue(
            McpServerController.DEFAULT_PORT in McpServerController.VALID_PORTS,
            "The default is outside the range, so the app opens on a port no surface would accept.",
        )
    }
}
