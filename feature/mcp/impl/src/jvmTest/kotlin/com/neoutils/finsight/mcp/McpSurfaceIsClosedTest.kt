package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The server offers exactly the tools it was decided to offer, and what it does not reach is
 * written down.**
 *
 * The equality is checked in both directions, and the second one is the reason this exists. A tool
 * that appears without being declared is one that entered the surface by being written rather than
 * by being decided on. A tool that is declared and never registered is worse, because **absence does
 * not manifest**: nobody notices a missing capability by reading the list of what exists, and an
 * agent that cannot find how to delete a posting concludes the app cannot delete postings — a false
 * statement, made confidently, that blocks the very action that would fix it.
 *
 * It is the same shape as `AgentInstructionsTest`, which compares `AGENTS.md` with the skills on
 * disk for the same reason: a list nothing checks drifts, and the drift is silent.
 *
 * The registry is asked for the **production** list — [mcpTools] over a dependency set the test
 * assembles — rather than for a list of the test's own, which would be free to drift away from the
 * one the socket announces.
 */
class McpSurfaceIsClosedTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /** The tools the desktop would announce, built exactly the way the desktop builds them. */
    private fun announcedTools(): List<McpTool> = AgentWorld().use { it.tools() }

    // ----------------------------------------------------------------------------------
    // The surface is closed
    // ----------------------------------------------------------------------------------

    @Test
    fun `the tools the server announces are exactly the tools declared`() {
        val announced = announcedTools().map { it.name }.toSortedSet()
        val declared = McpSurface.offered.map { it.wireName }.toSortedSet()

        assertEquals(
            declared,
            announced,
            "The registry and the declaration disagree about what this server offers.\n" +
                (announced - declared).joinToString("\n") {
                    "  ANNOUNCED BUT UNDECLARED: $it — it entered the surface without a decision"
                } +
                (declared - announced).joinToString("\n") {
                    "  DECLARED BUT UNANNOUNCED: $it — it left the surface and nobody noticed"
                },
        )
    }

    @Test
    fun `a name the surface never decided on cannot be announced`() {
        val decided = McpToolName.entries.map { it.wireName }.toSet()
        val strangers = announcedTools().map { it.name }.filterNot { it in decided }

        assertEquals(
            emptyList(),
            strangers,
            "A tool may only be announced under a name `McpToolName` declares.\n" +
                strangers.joinToString("\n") { "  UNKNOWN NAME: $it" },
        )
    }

    @Test
    fun `the surface holds the number of tools it was decided to hold`() {
        assertEquals(
            TOOLS_IN_THE_SURFACE,
            McpToolName.entries.size,
            "The surface changed size. That is allowed — it is a decision — but it is a " +
                "decision somebody makes here, not a side effect of adding a constant.",
        )
        assertEquals(
            TOOLS_IN_THE_SURFACE,
            McpToolName.entries.map { it.wireName }.toSet().size,
            "Two tools share a name; the agent, and the activity log, would not tell them apart.",
        )
    }

    @Test
    fun `each axis governs the number of tools the user is told it governs`() {
        // Not decoration: the settings screen tells the user how many tools each switch grants and
        // how many a withheld one holds back, and a switch whose effect is not stated is granted
        // blind. These are those numbers.
        assertEquals(
            mapOf(
                McpPermissionAxis.READ to 20,
                McpPermissionAxis.RECORD to 15,
                McpPermissionAxis.REMOVE to 8,
                McpPermissionAxis.OPERATE to 15,
            ),
            McpToolName.entries.groupingBy { it.axis }.eachCount(),
        )
    }

    @Test
    fun `removing is an axis of its own, and every removal is on it`() {
        // The requirement says "remover definitivamente", naming no entity, and its scenario is
        // literal: granting "record and edit" without "remove" leaves an agent that *creates and
        // alters, and does not remove*. A single `delete_*` parked on the recording axis would
        // remove under that grant, and the user would have granted it without being told.
        assertEquals(
            McpToolName.entries.filter { it.wireName.startsWith("delete_") }.toSet(),
            McpToolName.entries.filter { it.axis == McpPermissionAxis.REMOVE }.toSet(),
            "Every removal belongs to the removal axis, and nothing else does.",
        )
    }

    @Test
    fun `each family is governed by the axis that matches it`() {
        val misplaced = McpToolName.entries.filterNot { tool ->
            when (tool.family) {
                McpToolFamily.QUESTIONS, McpToolFamily.CATALOGUE ->
                    tool.axis == McpPermissionAxis.READ

                McpToolFamily.REGISTRATION ->
                    tool.axis == McpPermissionAxis.RECORD || tool.axis == McpPermissionAxis.REMOVE

                McpToolFamily.OPERATIONS ->
                    tool.axis == McpPermissionAxis.OPERATE
            }
        }

        assertEquals(
            emptyList(),
            misplaced,
            "The four families are the four axes, with removal split out of registration.\n" +
                misplaced.joinToString("\n") { "  ${it.wireName}: ${it.family} on ${it.axis}" },
        )
    }

    @Test
    fun `read-only sees the two families that only read`() {
        // The one grant a freshly enabled server carries, so what it reaches is worth stating.
        assertEquals(
            McpToolName.entries
                .filter { it.family == McpToolFamily.QUESTIONS || it.family == McpToolFamily.CATALOGUE }
                .toSet(),
            McpToolName.entries.filter { it.axis == McpPermissionAxis.READ }.toSet(),
        )
    }

    // ----------------------------------------------------------------------------------
    // What is left out, and why
    // ----------------------------------------------------------------------------------

    @Test
    fun `every capability left out is left out for a stated reason`() {
        val silent = McpSurface.exclusions.filter { it.capability.isBlank() || it.reason.isBlank() }

        assertTrue(McpSurface.exclusions.isNotEmpty(), "The exclusion list is empty.")
        assertEquals(
            emptyList(),
            silent,
            "An exclusion without a reason is indistinguishable from an oversight, which is the " +
                "one thing the list exists to rule out.\n" +
                silent.joinToString("\n") { "  NO REASON: ${it.capability}" },
        )
    }

    /**
     * The three the specification forbids by name, as opposed to the ones merely not built.
     *
     * They share a shape: the damage is asymmetric — one call reaches every figure in the app — and
     * it is silent, because none of them produces a posting that would show it happened.
     */
    @Test
    fun `the capabilities a requirement withholds are the ones declared as withheld`() {
        assertEquals(
            WITHHELD_BY_REQUIREMENT,
            McpSurface.withheld.map { it.capability }.toSortedSet(),
            "The withheld list and the requirements disagree about what may not be offered.",
        )
    }

    // ----------------------------------------------------------------------------------
    // No tool writes a rate, moves the base, or reconfigures the server
    // ----------------------------------------------------------------------------------

    /**
     * The declaration side, which is airtight because the surface is closed: a tool that wrote a
     * rate would have to be one of the fifty-eight, and none of them is about rates, about the base
     * currency, or about the server itself.
     */
    @Test
    fun `no tool of the surface is named for a rate, the base currency, or the server`() {
        val suspects = McpToolName.entries.filter { tool ->
            FORBIDDEN_SUBJECTS.any { it in tool.wireName }
        }

        assertEquals(
            emptyList(),
            suspects,
            "A tool naming one of the withheld subjects.\n" +
                suspects.joinToString("\n") { "  ${it.wireName}" },
        )
    }

    /**
     * The implementation side. Nothing stops a tool that is legitimately named from writing a rate
     * on the side, so what a tool is allowed to *hold* is stated too.
     *
     * `IExchangeRateRepository` is out whether it reads or writes: reaching the archive directly is
     * choosing a rate, and choosing a rate is the reducer's alone. The base currency is pinned by
     * `BaseCurrencyReachTest` across the whole repository already, so only the write is named here —
     * two owners for one rule is how the two come to disagree.
     */
    @Test
    fun `no tool holds what would let it write a rate, move the base, or reconfigure the server`() {
        val defects = toolSources.flatMap { file ->
            val text = file.readText()
            val path = file.relativeTo(repoRoot).invariantSeparatorsPath

            val held = FORBIDDEN_REFERENCES
                .filterKeys { it in text }
                .map { (reference, why) -> "$path holds `$reference` — $why" }

            val movesTheBase = "IBaseCurrencyRepository" in text && MOVES_THE_BASE in text

            held + listOfNotNull(
                if (movesTheBase) {
                    "$path moves the base currency — one call re-denominates every consolidated " +
                        "figure in the app, closed months included"
                } else {
                    null
                },
            )
        }

        assertEquals(
            emptyList(),
            defects,
            "A tool reaching something the surface withholds.\n" + defects.joinToString("\n") { "  $it" },
        )
    }

    /**
     * The floor under the two scans above: with no tool written yet they would pass over an empty
     * list, and would go on passing if the way a tool is declared ever stopped matching. The test
     * double in this suite is a real implementation of [McpTool], so recognising it is exactly the
     * question — and the server's own sources, which merely *mention* the type, must not be
     * mistaken for tools.
     */
    @Test
    fun `the scan recognises a tool by how one is declared`() {
        val double = File(repoRoot, "$MODULE/src/jvmTest/kotlin/com/neoutils/finsight/mcp/SpyTool.kt")
        assertTrue(double.isFile, "the test double moved; the scan below proves nothing")
        assertTrue(DECLARES_A_TOOL in double.readText(), "a tool declaration is no longer recognised")

        val server = File(repoRoot, "$MODULE/src/jvmMain/kotlin/com/neoutils/finsight/mcp")
        listOf("DesktopMcpServerController.kt", "AgentActivityJournal.kt", "McpTool.kt").forEach {
            assertTrue(
                DECLARES_A_TOOL !in File(server, it).readText(),
                "$it names `McpTool` without being one, and the scan took it for a tool",
            )
        }
    }

    /** Every source in this module that declares something implementing [McpTool]. */
    private val toolSources: List<File>
        get() = File(repoRoot, "$MODULE/src/jvmMain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { DECLARES_A_TOOL in it.readText() }
            .toList()

    private companion object {

        const val MODULE = "feature/mcp/impl"

        /** Ten questions, ten catalogue reads, twenty-three registrations, fifteen operations. */
        const val TOOLS_IN_THE_SURFACE = 58

        /**
         * A type declaring [McpTool] as a **supertype** — which is what a tool is — rather than any
         * of the places the server names the type in passing.
         */
        val DECLARES_A_TOOL = Regex("""(?:\)|\bclass\s+\w+|\bobject\s+\w+)\s*:\s*McpTool\b""")

        /** The one write on `IBaseCurrencyRepository`; reading it is governed repo-wide elsewhere. */
        val MOVES_THE_BASE = Regex("""\.set\s*\(""")

        val WITHHELD_BY_REQUIREMENT = sortedSetOf(
            "Writing an exchange rate, and the currency catalogue the rates hang from",
            "Changing the base currency",
            "Administering the server itself — its port, its token, its permissions",
        )

        /**
         * Fragments no tool name may contain. `_port` rather than `port`, because `get_report_stats`
         * holds the letters of one and none of the meaning.
         */
        val FORBIDDEN_SUBJECTS = listOf(
            "exchange_rate",
            "_rate",
            "rate_",
            "base_currency",
            "server",
            "_port",
            "_token",
            "token_",
            "permission",
        )

        val FORBIDDEN_REFERENCES = mapOf(
            "IExchangeRateRepository" to
                "reaching the rate archive is choosing a rate, and the reducer is the only " +
                "thing in the app that may",
            "McpServerController" to
                "a tool that can start, stop or re-port the server it is running inside",
            "McpServerSettings" to
                "the server's own port, token and preferences",
        )
    }
}
