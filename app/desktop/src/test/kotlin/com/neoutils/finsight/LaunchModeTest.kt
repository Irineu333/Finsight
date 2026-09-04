package com.neoutils.finsight

import com.neoutils.finsight.feature.mcp.api.McpLaunchCommand
import com.neoutils.finsight.feature.mcp.api.ofThisProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **One executable, and the argument decides which program inside it runs** (design D1).
 *
 * Two failures are being held off here, and neither one announces itself. A launcher that took the
 * stdio mode on something other than the exact argument would open no window for a user whose
 * desktop passed a flag along, and the only symptom would be an app that does not start. A launcher
 * that missed the argument would open a window on the machine of somebody whose agent just launched
 * it — and answer the client with a pipe carrying no protocol at all.
 *
 * The decision is asserted as a value rather than through [main], because calling that in the
 * window mode opens one.
 */
class LaunchModeTest {

    @Test
    fun `no argument opens the window`() {
        assertEquals(
            LaunchMode.WINDOW,
            LaunchMode.of(emptyArray()),
            "The app the user double-clicks is launched with nothing, and it has to open.",
        )
    }

    @Test
    fun `the stdio argument starts the headless mode`() {
        assertEquals(
            LaunchMode.MCP_STDIO,
            LaunchMode.of(arrayOf("--mcp")),
            "A client launching the executable with the documented argument got a window.",
        )
    }

    @Test
    fun `an argument nobody recognises still opens the window`() {
        listOf("--headless", "-mcp", "mcp", "--mcp=true", "--serve").forEach { argument ->
            assertEquals(
                LaunchMode.WINDOW,
                LaunchMode.of(arrayOf(argument)),
                "`$argument` is not the stdio argument, and anything that is not it opens the app.",
            )
        }
    }

    @Test
    fun `the stdio argument is found among others`() {
        assertEquals(
            LaunchMode.MCP_STDIO,
            LaunchMode.of(arrayOf("--verbose", "--mcp")),
            "A client that passes more than one argument was answered with a window.",
        )
    }

    /**
     * The two ends of the argument, checked against each other: what the settings section hands a
     * user to copy is what this dispatch answers to. They are on opposite sides of the app and the
     * only thing keeping them together is the constant they both read, so the assertion is that
     * reading it is what they in fact do.
     */
    @Test
    fun `the arguments the section publishes are the ones that start the stdio mode`() {
        val published = assertNotNull(
            McpLaunchCommand.ofThisProcess(),
            "This process cannot say what launched it, so there is nothing to publish or match.",
        )

        assertEquals(
            LaunchMode.MCP_STDIO,
            LaunchMode.of(published.args.toTypedArray()),
            "A client configured with exactly what the section shows would open a window.",
        )
    }
}
