package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **What the user granted, as a client experiences it.**
 *
 * Everything here goes over a real socket, because every claim in `mcp-permissions` is about
 * something only the wire can show: what `tools/list` *announces*, what a call by name comes back
 * with, what the handshake carries, and what reaches a session that is already open. A permission
 * checked by calling a Kotlin function proves the `if` and none of that.
 *
 * The two halves are deliberately separate and both required. Filtering the announcement is the
 * point — the agent does not attempt what it may not do, and does not spend context on it — and the
 * refusal on execution stays, because the announcement is a *consequence* of the permission and not
 * its only application (design D5).
 *
 * And filtering alone is not enough, which is the correction D13 makes to D5: a client that knows
 * the app only by the list of tools cannot tell a withheld capability from one the app lacks. Asked
 * to delete a posting on a prototype with the removal axis off, a real agent answered *"there is no
 * delete tool on this server"* — false, said confidently to the app's owner, and it blocks the one
 * action that would resolve the case. So the handshake declares what is withheld, and a tool called
 * by name refuses saying it *exists and is not authorised*.
 */
class McpPermissionsOverTheProtocolTest {

    // ----------------------------------------------------------------------------------
    // 12.1 — the initial state, and what survives a restart
    // ----------------------------------------------------------------------------------

    /**
     * The app opened for the first time after the update: nothing was chosen, so nothing runs and
     * nothing is offered.
     */
    @Test
    fun `before anything is configured the server is off and offers nothing`() = runTest {
        val port = freePort()
        val settings = MapSettings("mcp_server_port" to port)

        McpServerHarness(settings = settings, tools = spies(), permissions = null).use { harness ->
            harness.controller.start()

            assertEquals(
                McpServerState.Stopped,
                harness.controller.state.value,
                "A server nobody switched on came up.",
            )
            assertTrue(
                withContext(Dispatchers.IO) { Loopback.refusesConnection(port) },
                "Something is listening at $port, so a tool could be reached on an installation " +
                    "where no choice was ever made.",
            )
        }
    }

    /**
     * The user flips the one switch there is and touches nothing else. Reading is what they get:
     * writing waits for a second, explicit act.
     */
    @Test
    fun `switched on for the first time it reads, and changes nothing`() = runTest {
        val port = freePort()

        McpServerHarness(tools = spies(), permissions = null).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            assertEquals(
                McpPermissionAxis.INITIAL,
                harness.controller.permissions.value,
                "A freshly enabled server granted something other than reading.",
            )

            withContext(Dispatchers.IO) {
                val client = McpConversation(port, token).open()

                assertEquals(
                    listOf(READER),
                    client.listTools().announcedToolNames(),
                    "A server switched on for the first time announced more than reading.",
                )

                val write = client.callTool(RECORDER)
                assertTrue(write.isToolError(), "An unauthorised write was carried out.")
            }

            assertEquals(0, spy(RECORDER).calls, "The write ran despite never being granted.")

            harness.controller.stop()
        }
    }

    /** A grant is a choice like the switch and the port, and it outlives the process the same way. */
    @Test
    fun `a grant survives the app being closed and opened`() = runTest {
        val settings = MapSettings()
        val port = freePort()

        McpServerHarness(settings = settings, permissions = null).use { firstRun ->
            firstRun.controller.setPort(port)
            firstRun.controller.setEnabled(true)
            firstRun.controller.setPermission(McpPermissionAxis.REMOVE, granted = true)
            firstRun.controller.stop()
        }

        McpServerHarness(settings = settings, tools = spies(), permissions = null).use { secondRun ->
            secondRun.controller.start()

            assertEquals(
                setOf(McpPermissionAxis.READ, McpPermissionAxis.REMOVE),
                secondRun.controller.permissions.value,
                "The grant did not survive the app closing, so the user would have to make it again.",
            )

            val token = assertNotNull(secondRun.controller.token.value)
            withContext(Dispatchers.IO) {
                assertEquals(
                    listOf(READER, REMOVER).sorted(),
                    McpConversation(port, token).open().listTools().announcedToolNames().sorted(),
                    "The restored grant is not what the server announces.",
                )
            }

            secondRun.controller.stop()
        }
    }

    // ----------------------------------------------------------------------------------
    // 12.2 — the permission decides which tools exist
    // ----------------------------------------------------------------------------------

    /** With removal withheld, nothing that removes is offered. */
    @Test
    fun `a withheld axis is not announced at all`() = runTest {
        withPermissions(McpPermissionAxis.entries.toSet() - McpPermissionAxis.REMOVE) { _, client ->
            val announced = client.listTools().announcedToolNames()

            assertTrue(
                REMOVER !in announced,
                "A removal was announced with the removal axis withheld: $announced",
            )
            assertEquals(
                listOf(READER, RECORDER, OPERATOR).sorted(),
                announced.sorted(),
                "Withholding one axis changed what the other three offer.",
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // 12.3 / 12.4b — called by name anyway
    // ----------------------------------------------------------------------------------

    /**
     * Not announced is not the same as not enforced. The announcement is a consequence of the
     * permission, and a client that kept a name from an earlier session — or guessed one — is
     * refused before anything of the tool runs.
     */
    @Test
    fun `a withheld tool called by name is refused, and nothing runs`() = runTest {
        withPermissions(setOf(McpPermissionAxis.READ)) { harness, client ->
            val response = client.callTool(REMOVER)

            assertTrue(
                response.isToolError(),
                "A tool of a withheld axis answered as though it had run: ${response.body}",
            )
            assertEquals(0, spy(REMOVER).calls, "The withheld tool ran.")

            val log = harness.activity.observeAll().first()
            assertEquals(1, log.size, "The blocked attempt left no trace for the user: $log")
            assertEquals(AgentActivity.Outcome.REFUSED, log.single().outcome)
            assertEquals(REMOVER, log.single().operation)
        }
    }

    /**
     * **"Not authorised" and "does not exist" are different answers, and the difference is the whole
     * of D13.**
     *
     * An agent told the name is unknown reports back that the app cannot do the thing — a false
     * statement about the app, and one that hides the switch that would fix it.
     */
    @Test
    fun `the refusal says the operation exists, never that it is unknown`() = runTest {
        withPermissions(setOf(McpPermissionAxis.READ)) { _, client ->
            val withheld = client.callTool(REMOVER).toolText()

            assertTrue(
                "exists" in withheld && "not authorised" in withheld,
                "The refusal does not say the operation exists and is unauthorised: $withheld",
            )
            assertTrue(
                "not found" !in withheld.lowercase(),
                "A withheld operation was reported as one the app does not have: $withheld",
            )
            assertTrue(
                McpPermissionNotice.WHERE_TO_GRANT in withheld,
                "The refusal does not say where the capability is granted: $withheld",
            )

            // The contrast, from the same server in the same session: a name that really is not
            // there is answered differently, or the distinction would exist only in the prose.
            val unknown = client.callTool("get_horoscope")
            assertTrue(
                "not found" in unknown.toolText().lowercase(),
                "A name the surface never had was answered as though it were withheld: " +
                    unknown.toolText(),
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // 12.4 — moving a switch reaches whoever is already connected
    // ----------------------------------------------------------------------------------

    /** Granting mid-session: the client is told, and sees the tools without reconnecting. */
    @Test
    fun `granting an axis reaches a session already open`() = runTest {
        val port = freePort()

        McpServerHarness(tools = spies()).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            McpPermissionAxis.entries.forEach {
                harness.controller.setPermission(it, granted = it == McpPermissionAxis.READ)
            }
            val token = assertNotNull(harness.controller.token.value)

            val client = withContext(Dispatchers.IO) { McpConversation(port, token).open() }

            withContext(Dispatchers.IO) { client.eventStream() }.use { stream ->
                assertEquals(listOf(READER), withContext(Dispatchers.IO) { client.listTools() }.announcedToolNames())

                harness.controller.setPermission(McpPermissionAxis.REMOVE, granted = true)

                assertTrue(
                    LIST_CHANGED in stream.await(LIST_CHANGED),
                    "The client was never told the list changed, so it would have to reconnect " +
                        "to find out: ${stream.text}",
                )
                assertEquals(
                    listOf(READER, REMOVER).sorted(),
                    withContext(Dispatchers.IO) { client.listTools() }.announcedToolNames().sorted(),
                    "The same session did not start seeing the newly granted tool.",
                )
            }

            harness.controller.stop()
        }
    }

    /** Revoking mid-session: the same message, and the tools stop being offered from then on. */
    @Test
    fun `revoking an axis reaches a session already open`() = runTest {
        val port = freePort()

        McpServerHarness(tools = spies()).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val client = withContext(Dispatchers.IO) { McpConversation(port, token).open() }

            withContext(Dispatchers.IO) { client.eventStream() }.use { stream ->
                assertTrue(
                    REMOVER in withContext(Dispatchers.IO) { client.listTools() }.announcedToolNames(),
                )

                harness.controller.setPermission(McpPermissionAxis.REMOVE, granted = false)

                assertTrue(
                    LIST_CHANGED in stream.await(LIST_CHANGED),
                    "A revocation did not reach the client: ${stream.text}",
                )
                assertTrue(
                    REMOVER !in withContext(Dispatchers.IO) { client.listTools() }.announcedToolNames(),
                    "A revoked tool is still being offered to the session it was revoked under.",
                )
            }

            harness.controller.stop()
        }
    }

    // ----------------------------------------------------------------------------------
    // 12.4a / 12.4d — what is withheld is declared, and it is not a second list
    // ----------------------------------------------------------------------------------

    /**
     * The handshake says what was granted, what was withheld, that the withholding is the user's own
     * choice, and where it is reversed.
     */
    @Test
    fun `the handshake declares what is granted and what is withheld`() = runTest {
        val port = freePort()

        McpServerHarness(tools = spies(), permissions = setOf(McpPermissionAxis.READ)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val instructions = assertNotNull(
                withContext(Dispatchers.IO) { McpConversation(port, token).initialize() }
                    .sessionInstructions(),
                "The handshake carried no instructions, so a withheld capability is invisible.",
            )

            assertTrue(
                "Granted right now: read" in instructions,
                "The handshake does not say what is granted: $instructions",
            )
            listOf("record and edit", "remove", "operate").forEach { capability ->
                assertTrue(
                    capability in instructions.substringAfter("Withheld by this user:"),
                    "`$capability` is withheld and the handshake does not say so: $instructions",
                )
            }
            assertTrue(
                McpPermissionNotice.WHERE_TO_GRANT in instructions,
                "The handshake does not say the withholding is the user's, and reversible: " +
                    instructions,
            )

            harness.controller.stop()
        }
    }

    /**
     * **The declaration is not a second `tools/list` arriving by another channel.**
     *
     * Naming the withheld tools would hand back exactly the context the filtering saved, and would
     * teach an agent to ask for something by a name it may not use. What is declared is the
     * capability — so no wire identifier may appear in the text at all, tool name or argument.
     */
    @Test
    fun `the declaration names capabilities and no tools`() = runTest {
        val port = freePort()

        McpServerHarness(tools = spies(), permissions = setOf(McpPermissionAxis.READ)).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val instructions = assertNotNull(
                withContext(Dispatchers.IO) { McpConversation(port, token).initialize() }
                    .sessionInstructions(),
            )

            val named = McpToolName.entries.map { it.wireName }.filter { it in instructions }
            assertEquals(
                emptyList(),
                named,
                "The handshake enumerated tools, which is the second list this must not be:\n" +
                    named.joinToString("\n") { "  NAMED: $it" },
            )

            val identifiers = WIRE_IDENTIFIER.findAll(instructions).map { it.value }.toList()
            assertEquals(
                emptyList(),
                identifiers,
                "The handshake carries wire identifiers — a tool or an argument by its calling " +
                    "name: $identifiers",
            )

            harness.controller.stop()
        }
    }

    /**
     * **The situation the simulation caught.**
     *
     * With removal withheld, the tool is nowhere in the list — and the session still carries what an
     * agent needs to answer *"the app removes postings, and it is waiting on your permission"*
     * instead of *"the app cannot remove postings"*. The second is false, and it is what an agent
     * with only the filtered list says.
     */
    @Test
    fun `with removal withheld the session still knows removal exists`() = runTest {
        val port = freePort()

        McpServerHarness(
            tools = spies(),
            permissions = McpPermissionAxis.entries.toSet() - McpPermissionAxis.REMOVE,
        ).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            val (instructions, announced) = withContext(Dispatchers.IO) {
                val client = McpConversation(port, token)
                val handshake = client.initialize().sessionInstructions()
                client.notifyInitialized()
                handshake to client.listTools().announcedToolNames()
            }

            assertTrue(REMOVER !in announced, "The list still offers removal; the case is not the one.")

            val withheldClause = assertNotNull(instructions)
                .substringAfter("Withheld by this user:", missingDelimiterValue = "")
            assertTrue(
                "remove" in withheldClause,
                "Removing is absent from the list and absent from the declaration, so the session " +
                    "cannot tell it apart from a capability the app lacks: $instructions",
            )
            assertTrue(
                "waiting on their permission" in assertNotNull(instructions),
                "Nothing tells the agent what to answer, which is where the false report came " +
                    "from: $instructions",
            )

            harness.controller.stop()
        }
    }

    // ----------------------------------------------------------------------------------
    // 12.5 — the four are independent
    // ----------------------------------------------------------------------------------

    /**
     * Granting one grants nothing else — asserted for each of the four, against the whole surface,
     * so it cannot pass by the axes happening to overlap in one direction.
     *
     * The two scenarios of the requirement are inside this: recording without removing leaves an
     * agent that creates and alters and does not remove, and recording without operating leaves one
     * that records postings and neither pays an invoice nor transfers.
     */
    @Test
    fun `granting one axis grants no other`() = runTest {
        McpPermissionAxis.entries.forEach { axis ->
            withPermissions(setOf(axis)) { _, client ->
                assertEquals(
                    setOf(nameOf(axis)),
                    client.listTools().announcedToolNames().toSet(),
                    "Granting $axis offered something governed by another axis.",
                )

                (McpPermissionAxis.entries - axis).forEach { other ->
                    val response = client.callTool(nameOf(other))
                    assertTrue(
                        response.isToolError(),
                        "Granting $axis carried $other along: ${response.toolText()}",
                    )
                    assertEquals(0, spy(nameOf(other)).calls, "A tool of $other ran under $axis.")
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // 12.6 — read-only, over the whole surface
    // ----------------------------------------------------------------------------------

    /**
     * The grant a freshly enabled server carries, measured against the tools the desktop really
     * builds: the twenty of the two reading families, and not one of the thirty-six that write.
     */
    @Test
    fun `read-only announces exactly the questions and the catalogue`() = runTest {
        val port = freePort()

        AgentWorld().use { world ->
            McpServerHarness(
                tools = world.tools(),
                permissions = setOf(McpPermissionAxis.READ),
            ).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = assertNotNull(harness.controller.token.value)

                val announced = withContext(Dispatchers.IO) {
                    McpConversation(port, token).open().listTools().announcedToolNames()
                }.toSortedSet()

                val reading = McpToolName.entries
                    .filter { it.family == McpToolFamily.QUESTIONS || it.family == McpToolFamily.CATALOGUE }
                    .map { it.wireName }
                    .toSortedSet()

                assertEquals(
                    reading,
                    announced,
                    "Read-only does not announce exactly the two reading families.\n" +
                        (announced - reading).joinToString("\n") { "  ANNOUNCED AND NOT A READ: $it" } +
                        (reading - announced).joinToString("\n") { "  A READ AND NOT ANNOUNCED: $it" },
                )
                assertEquals(
                    McpSurface.toolCountByAxis.getValue(McpPermissionAxis.READ),
                    announced.size,
                    "The number the settings section tells the user reading grants is not the " +
                        "number the server announces under it.",
                )

                harness.controller.stop()
            }
        }
    }

    /**
     * The same question asked of the whole surface rather than of a double: with removing withheld
     * and everything else granted, not one of the eight removals is announced — and what *is*
     * announced is exactly what the declaration says those three axes reach.
     */
    @Test
    fun `with removing withheld the surface announces no removal at all`() = runTest {
        val port = freePort()
        val granted = McpPermissionAxis.entries.toSet() - McpPermissionAxis.REMOVE

        AgentWorld().use { world ->
            McpServerHarness(tools = world.tools(), permissions = granted).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = assertNotNull(harness.controller.token.value)

                val announced = withContext(Dispatchers.IO) {
                    McpConversation(port, token).open().listTools().announcedToolNames()
                }.toSortedSet()

                assertEquals(
                    emptyList(),
                    announced.filter { it.startsWith("delete_") },
                    "A removal was announced with the removal axis withheld.",
                )
                assertEquals(
                    McpSurface.offeredUnder(granted).map { it.wireName }.toSortedSet(),
                    announced,
                    "What the socket announces is not what the surface says those axes reach.",
                )

                harness.controller.stop()
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // The world these run in
    // ----------------------------------------------------------------------------------

    /**
     * One tool per axis, each counting its own runs.
     *
     * Real names, because the axis is read off the name: a double with a name of its own would be
     * proving the permission over a surface the app does not have. The count is what every "nothing
     * ran" claim rests on — a refusal that happens after the body ran is not a refusal.
     */
    private val doubles: Map<String, SpyTool> = McpPermissionAxis.entries.associate { axis ->
        nameOf(axis) to SpyTool(
            name = nameOf(axis),
            effect = if (axis == McpPermissionAxis.READ) McpToolEffect.READS else McpToolEffect.CHANGES,
            answer = {
                McpToolResult(
                    text = "done",
                    summary = "an act on the ${axis.name.lowercase()} axis",
                    reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 1),
                )
            },
        )
    }

    private fun spies(): List<McpTool> = doubles.values.toList()

    private fun spy(name: String): SpyTool = doubles.getValue(name)

    private suspend fun withPermissions(
        granted: Set<McpPermissionAxis>,
        block: suspend (McpServerHarness, McpConversation) -> Unit,
    ) {
        val port = freePort()

        McpServerHarness(tools = spies(), permissions = granted).use { harness ->
            harness.controller.setPort(port)
            harness.controller.setEnabled(true)
            val token = assertNotNull(harness.controller.token.value)

            withContext(Dispatchers.IO) {
                block(harness, McpConversation(port, token).open())
            }

            harness.controller.stop()
        }
    }

    private companion object {

        /** The tool each axis is exercised through, one real name apiece. */
        fun nameOf(axis: McpPermissionAxis): String = when (axis) {
            McpPermissionAxis.READ -> McpToolName.LIST_ACCOUNTS.wireName
            McpPermissionAxis.RECORD -> McpToolName.CREATE_TRANSACTION.wireName
            McpPermissionAxis.REMOVE -> McpToolName.DELETE_TRANSACTION.wireName
            McpPermissionAxis.OPERATE -> McpToolName.TRANSFER.wireName
        }

        val READER: String = nameOf(McpPermissionAxis.READ)
        val RECORDER: String = nameOf(McpPermissionAxis.RECORD)
        val REMOVER: String = nameOf(McpPermissionAxis.REMOVE)
        val OPERATOR: String = nameOf(McpPermissionAxis.OPERATE)

        const val LIST_CHANGED = "notifications/tools/list_changed"

        /**
         * Anything that reads as a name from the wire rather than a word from a sentence — every
         * tool of this surface and every argument of one is `lower_snake_case`, and English prose
         * has no such word.
         */
        val WIRE_IDENTIFIER = Regex("""\b[a-z]+(?:_[a-z]+)+\b""")
    }
}
