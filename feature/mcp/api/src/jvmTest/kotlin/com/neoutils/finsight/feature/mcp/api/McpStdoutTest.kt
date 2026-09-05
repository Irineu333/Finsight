package com.neoutils.finsight.feature.mcp.api

import kotlin.test.Test
import kotlin.test.assertSame

/**
 * **The protocol keeps the stream it was given, and everything else is moved off it.**
 *
 * In the stdio mode the standard output is the wire, and a single line written to it by anything
 * else lands inside a frame the client is parsing. Nothing reports that: the session simply stops
 * being understood. So the claim happens once, first, and this is the assertion that it is the
 * *original* stream that is kept and the diagnostics one that everything else is pointed at.
 *
 * It is one test method and not three because the subject is a process-wide fact with an order to
 * it: a second method would either observe the claim the first one made or make its own, and which
 * of those happened would depend on the order the runner chose.
 */
class McpStdoutTest {

    @Test
    fun `claiming keeps the original stream for the protocol and moves everything else to stderr`() {
        val standardOutput = System.out

        try {
            McpStdout.claim()

            assertSame(
                standardOutput,
                McpStdout.protocol,
                "The protocol was given something other than the stream that was standing.",
            )
            assertSame(
                System.err,
                System.out,
                "Something written to System.out would still land on the protocol's stream.",
            )

            // The second claim of a process that already made one: what it must never do is hand
            // the protocol the diagnostics stream the first claim installed.
            McpStdout.claim()

            assertSame(
                standardOutput,
                McpStdout.protocol,
                "A second claim replaced the stream the protocol is written on.",
            )
        } finally {
            System.setOut(standardOutput)
        }
    }
}
