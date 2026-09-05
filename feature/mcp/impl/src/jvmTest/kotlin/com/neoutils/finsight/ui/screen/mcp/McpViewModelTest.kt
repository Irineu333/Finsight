@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.mcp

import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ClearAgentActivityUseCase
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.McpLaunchCommand
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerFailure
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_port_error_in_use
import com.neoutils.finsight.resources.mcp_port_error_invalid
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the MCP section shows, and — as often — what it refuses to show.
 *
 * Three of these are the requirement rather than a nicety. The section reveals nothing to decide
 * before the switch, because a server that is off has no address to point anything at. It never
 * reads a socket off the switch, so a bind that failed is shown as failed. And the port's refusal
 * lands on the port's own field, because that is where the user can do something about it.
 *
 * Two of them read the shipped strings instead of the state, because what the section *says* is
 * also what it shows: a sentence the app kept saying after it stopped being true would be caught by
 * nothing else in the suite.
 */
class McpViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ------------------------------------------------------------------------------
    // 13.1 — the order of the section
    // ------------------------------------------------------------------------------

    @Test
    fun `with the server off there is nothing else to decide`() = runTest {
        // With a command to show, so that having one is not what puts a decision before the switch.
        val controller = FakeController(launchCommand = installedAt(MACOS))
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        assertFalse(
            state().showsDetails,
            "the section offered the command, the address, the token or the permissions before " +
                "there was a server to connect to",
        )

        viewModel.onAction(McpAction.SetEnabled(true))

        assertTrue(state().showsDetails, "switching the server on revealed nothing")
        assertEquals(listOf("setEnabled(true)"), controller.calls)
    }

    // ------------------------------------------------------------------------------
    // 13.2a — the state does not lie
    // ------------------------------------------------------------------------------

    @Test
    fun `a server that was switched on and did not come up is not shown as up`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        controller.serverState.value = McpServerState.Running(port = 8477, sessions = 0)
        assertTrue(state().isRunning)

        // The drop *after* the start: nothing here is derived from the switch, so it lands.
        controller.serverState.value = McpServerState.Failed(port = 8477, cause = McpServerFailure.PORT_IN_USE)

        assertFalse(state().isRunning, "a failed bind was still being reported as up")
        assertFalse(state().hasConnectedClient)
        assertTrue(state().isEnabled, "the switch is still on — it is the socket that is down")
    }

    // ------------------------------------------------------------------------------
    // 13.2b — an axis says what it hands over
    // ------------------------------------------------------------------------------

    @Test
    fun `every axis states how many tools it grants, and a withheld one how many it holds`() = runTest {
        val controller = FakeController(enabled = true, permissions = setOf(McpPermissionAxis.READ))
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        val permissions = state().permissions

        assertEquals(
            McpPermissionAxis.entries.toList(),
            permissions.map { it.axis },
            "the section stopped offering one of the four axes",
        )
        permissions.forEach { permission ->
            assertEquals(
                controller.toolCountByAxis.getValue(permission.axis),
                permission.toolCount,
                "the count shown for ${permission.axis} is not the one the socket announces",
            )
            assertTrue(permission.toolCount > 0, "an axis that grants nothing is a switch with no effect")
        }
        assertEquals(
            listOf(true, false, false, false),
            permissions.map { it.isGranted },
            "a freshly enabled server grants reading and nothing else",
        )
    }

    @Test
    fun `granting one axis grants nothing else`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)

        viewModel.onAction(McpAction.SetPermission(McpPermissionAxis.REMOVE, granted = true))

        assertEquals(listOf("setPermission(REMOVE, true)"), controller.calls)
        assertEquals(
            setOf(McpPermissionAxis.READ, McpPermissionAxis.REMOVE),
            controller.permissions.value,
        )
    }

    // ------------------------------------------------------------------------------
    // 13.2c — enabled is not connected
    // ------------------------------------------------------------------------------

    @Test
    fun `being up and having someone on the other side are different facts`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        controller.serverState.value = McpServerState.Running(port = 8477, sessions = 0)

        assertTrue(state().isRunning)
        assertFalse(state().hasConnectedClient, "an idle server was reported as being read right now")
        assertEquals(0, state().sessions)

        controller.serverState.value = McpServerState.Running(port = 8477, sessions = 2)

        assertTrue(state().hasConnectedClient)
        assertEquals(2, state().sessions)

        viewModel.onAction(McpAction.DisconnectSessions)
        assertEquals(listOf("disconnectSessions"), controller.calls)
    }

    // ------------------------------------------------------------------------------
    // 13.2e — the port, and its refusal, on the field
    // ------------------------------------------------------------------------------

    @Test
    fun `what is not a port never reaches the server`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)
        subscribe(viewModel)

        // The sheet refuses to offer it, and this is the second half of the same rule: a caller
        // reaching the action from anywhere else cannot bind what the sheet would not collect.
        viewModel.onAction(McpAction.ChangePort(70000))
        viewModel.onAction(McpAction.ChangePort(0))
        // A privileged port is a port number and still outside the range: the process runs as the
        // user and the bind would be refused, in the shape a port already held arrives in.
        viewModel.onAction(McpAction.ChangePort(80))

        assertTrue(controller.calls.isEmpty(), "a port outside the range reached the server")
    }

    @Test
    fun `a port another program holds is said on the address, naming the port`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        controller.serverState.value = McpServerState.Failed(port = 8477, cause = McpServerFailure.PORT_IN_USE)

        val error = assertIs<UiText.ResWithArgs>(
            state().addressError,
            "the bind failure did not reach the row the user would fix it from",
        )
        assertEquals(Res.string.mcp_port_error_in_use, error.res)
        assertEquals(listOf(8477), error.args, "the message does not name the port that failed")

        // The address a client was configured with survives the failure: what changed is that the
        // row now says it is not answering, not that it stopped being the address.
        assertTrue(state().address.endsWith(":8477/mcp"))
    }

    @Test
    fun `the failure ends when the socket does come up`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        controller.serverState.value = McpServerState.Failed(port = 8477, cause = McpServerFailure.PORT_IN_USE)
        assertNotNull(state().addressError)

        controller.serverState.value = McpServerState.Running(port = 8500, sessions = 0)

        assertNull(state().addressError, "the row still reported a failure the socket had left behind")
    }

    @Test
    fun `changing the port moves the server`() = runTest {
        val controller = FakeController(enabled = true)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        viewModel.onAction(McpAction.ChangePort(8500))

        assertEquals(8500, state().port)
        assertEquals(listOf("setPort(8500)"), controller.calls)
    }

    // ------------------------------------------------------------------------------
    // The token is a secret on a screen
    // ------------------------------------------------------------------------------

    @Test
    fun `the token is masked until it is asked for`() = runTest {
        val controller = FakeController(enabled = true, token = "abcdef0123456789")
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        val masked = assertNotNull(state().displayedToken)
        assertFalse(
            masked.contains("abcdef"),
            "the token was on screen without the user having asked for it",
        )
        assertEquals("abcdef0123456789", state().token, "copying has to reach the real token")

        viewModel.onAction(McpAction.ToggleTokenVisibility)

        assertEquals("abcdef0123456789", state().displayedToken)
    }

    /**
     * The section is screenshotted and shared as a whole.
     *
     * A user opening Settings → MCP to share their screen asking for help, or to attach the section
     * to a bug report, hands over the connection block along with the token row. A row that says
     * `••••••••••••••••` over a block that says `Bearer <the token>` protects nothing: whoever reads
     * that string and can reach the loopback holds every capability that was granted.
     */
    @Test
    fun `the connection block hides the token the row above it hides`() = runTest {
        val controller = FakeController(enabled = true, token = "abcdef0123456789")
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        assertFalse(
            state().displayedConnectionSnippet.contains("abcdef0123456789"),
            "the block handed the token to a screenshot the row above it had masked",
        )
        assertTrue(
            state().connectionSnippet.contains("\"Authorization\": \"Bearer abcdef0123456789\""),
            "copying the block gave a client something it cannot authenticate with",
        )

        viewModel.onAction(McpAction.ToggleTokenVisibility)

        assertTrue(
            state().displayedConnectionSnippet.contains("\"Authorization\": \"Bearer abcdef0123456789\""),
            "asking to see the token left the block masked",
        )
        assertTrue(
            state().connectionSnippet.contains("\"Authorization\": \"Bearer abcdef0123456789\""),
            "copying stopped reaching the real token once it was on screen",
        )
    }

    @Test
    fun `the address is what a client is configured with`() = runTest {
        val controller = FakeController(enabled = true, port = 8500)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        assertEquals("http://127.0.0.1:8500/mcp", state().address)
    }

    // ------------------------------------------------------------------------------
    // The two ways in, side by side
    // ------------------------------------------------------------------------------

    @Test
    fun `the block a client is configured with names the executable of this installation`() = runTest {
        val controller = FakeController(enabled = true, launchCommand = installedAt(MACOS))
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        val launch = assertNotNull(state().launch, "the section had no command to hand a client")

        // Parsed rather than matched: a block the user pastes into a client's configuration is
        // only an instruction if it parses, and what the client reads out of it is these two keys.
        val server = Json.parseToJsonElement(launch.snippet)
            .jsonObject.getValue("mcpServers")
            .jsonObject.getValue("finsight")
            .jsonObject

        assertEquals(
            MACOS,
            server.getValue("command").jsonPrimitive.content,
            "the block did not carry the absolute path of the executable that is running",
        )
        assertEquals(
            listOf(McpLaunchCommand.STDIO_ARGUMENT),
            server.getValue("args").jsonArray.map { it.jsonPrimitive.content },
            "the block would launch the window instead of the protocol",
        )
        assertEquals(
            """claude mcp add finsight -- "$MACOS" ${McpLaunchCommand.STDIO_ARGUMENT}""",
            launch.line,
            "the one-line form is not the same command as the block",
        )
        assertEquals(
            McpCommandTarget.CLAUDE_CODE,
            launch.target,
            "the section opened on a client other than the one it is written for by default",
        )
    }

    /**
     * The path is a value the app read from the system, and the block is JSON.
     *
     * On Windows the executable lives behind backslashes, and `C:\Users\...` copied verbatim into a
     * client's configuration file is not a JSON string: the client rejects the whole file, and what
     * the user sees is a server that never starts.
     */
    @Test
    fun `a path with backslashes is still a JSON string`() = runTest {
        val controller = FakeController(enabled = true, launchCommand = installedAt(WINDOWS))
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        val launch = assertNotNull(state().launch)
        val server = Json.parseToJsonElement(launch.snippet)
            .jsonObject.getValue("mcpServers")
            .jsonObject.getValue("finsight")
            .jsonObject

        assertEquals(
            WINDOWS,
            server.getValue("command").jsonPrimitive.content,
            "the path came back out of the block as something else than it went in",
        )
        assertTrue(
            launch.line.contains(WINDOWS),
            "the one-line form is a shell command, and the path goes into it as it is",
        )
    }

    /**
     * The one-line form is one client's shorthand, and the user says which client.
     *
     * Three CLIs take a server as a single command, and each spells it its own way: two put the
     * executable behind `--`, one takes it positionally. What must not vary is what is being said —
     * the same executable, with the same argument — because the line is the block above written
     * another way, not a second configuration.
     */
    @Test
    fun `the one-line form is written in the words of the client the user picked`() = runTest {
        val controller = FakeController(enabled = true, launchCommand = installedAt(MACOS))
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        val expected = mapOf(
            McpCommandTarget.CLAUDE_CODE to """claude mcp add finsight -- "$MACOS" --mcp""",
            McpCommandTarget.GEMINI_CLI to """gemini mcp add finsight "$MACOS" --mcp""",
            McpCommandTarget.CODEX_CLI to """codex mcp add finsight -- "$MACOS" --mcp""",
        )

        assertEquals(
            McpCommandTarget.entries.toSet(),
            expected.keys,
            "a client was offered in the section without a line of its own being asserted here",
        )

        expected.forEach { (target, line) ->
            viewModel.onAction(McpAction.SelectCommandTarget(target))

            val launch = assertNotNull(state().launch)
            assertEquals(line, launch.line, "the line for ${target.label} is not what it takes")
            assertEquals(target, launch.target, "the section did not move to ${target.label}")
        }
    }

    /**
     * And whichever client is picked, the line launches this app and nothing else.
     *
     * The path and the argument are the two things a wrong line would get wrong quietly: a server
     * that opens the window instead of speaking the protocol looks, from the client's side, like an
     * app that hangs.
     */
    @Test
    fun `every one-line form carries this installation's executable and the stdio argument`() = runTest {
        val controller = FakeController(enabled = true, launchCommand = installedAt(WINDOWS))
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        McpCommandTarget.entries.forEach { target ->
            viewModel.onAction(McpAction.SelectCommandTarget(target))

            val line = assertNotNull(state().launch).line
            assertTrue(
                line.contains(WINDOWS),
                "the line for ${target.label} does not name this installation's executable: $line",
            )
            assertTrue(
                line.trimEnd().endsWith(McpLaunchCommand.STDIO_ARGUMENT),
                "the line for ${target.label} would launch the window instead of the protocol: $line",
            )
            assertTrue(
                line.contains("finsight"),
                "the line for ${target.label} does not name the server it is adding: $line",
            )
        }
    }

    /**
     * The two tabs are drawn as equals, and only one of them holds with the window closed.
     *
     * Which one the section opens on is therefore not a matter of taste: the command answers either
     * way, the address only while the window is up, and a section opening on the address would be
     * handing the user the narrower path as if it were the plain one.
     */
    @Test
    fun `the section opens on the command, with the address as the other way in`() = runTest {
        val controller = FakeController(
            enabled = true,
            token = "abcdef0123456789",
            launchCommand = installedAt(MACOS),
        )
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        assertTrue(state().showsConnectionTabs, "the section offered no choice between the two")
        assertEquals(
            McpConnectionTab.COMMAND,
            state().selectedConnectionTab,
            "the section opened on the path that only answers with the window up",
        )
        assertTrue(state().showsCommand, "the instruction the section opens on was not drawn")
        assertFalse(
            state().showsAddress,
            "the address was on screen beside the command instead of behind its own tab",
        )
    }

    @Test
    fun `choosing the address draws it, and choosing the command draws it back`() = runTest {
        val controller = FakeController(
            enabled = true,
            token = "abcdef0123456789",
            launchCommand = installedAt(MACOS),
        )
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        viewModel.onAction(McpAction.SelectConnectionTab(McpConnectionTab.ADDRESS))

        assertTrue(state().showsAddress, "choosing the address tab showed nothing")
        assertFalse(state().showsCommand, "the two tabs were on screen at once")
        assertEquals("http://127.0.0.1:8477/mcp", state().address, "the address is not copyable")
        assertEquals("abcdef0123456789", state().token, "copying has to reach the real token")
        assertTrue(
            state().connectionSnippet.contains("\"Authorization\": \"Bearer abcdef0123456789\""),
            "the block under the address tab stopped being copyable",
        )

        viewModel.onAction(McpAction.SelectConnectionTab(McpConnectionTab.COMMAND))

        assertTrue(state().showsCommand, "the command tab could not be reached again")
        assertFalse(state().showsAddress, "the address stayed on screen under the command tab")
        val launch = assertNotNull(state().launch, "the command stopped being copyable")
        assertTrue(launch.snippet.contains(MACOS), "the block came back without the executable")
    }

    /**
     * Changing tabs is not asking for the token.
     *
     * The tab row is the one control that puts the token on screen, and it is reached by a user
     * looking for a `url` rather than for the secret beside it. If arriving there unmasked the
     * token, the mask would be protecting the token from everyone except whoever pressed the tab.
     */
    @Test
    fun `arriving at the address tab does not reveal the token`() = runTest {
        val controller = FakeController(
            enabled = true,
            token = "abcdef0123456789",
            launchCommand = installedAt(MACOS),
        )
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        viewModel.onAction(McpAction.SelectConnectionTab(McpConnectionTab.ADDRESS))

        assertFalse(
            state().displayedToken.orEmpty().contains("abcdef"),
            "choosing the address tab put the token on screen without it being asked for",
        )
        assertFalse(
            state().displayedConnectionSnippet.contains("abcdef0123456789"),
            "the block handed the token to a screenshot the row above it had masked",
        )

        viewModel.onAction(McpAction.ToggleTokenVisibility)

        assertEquals(
            "abcdef0123456789",
            state().displayedToken,
            "asking for the token under the address tab showed nothing",
        )
    }

    /**
     * One tab is not a tab.
     *
     * Where the process cannot say what it was launched from there is no command to offer, and the
     * address stops being one of two ways in — it is the way in. A tab row holding a single option
     * would be an affordance that decides nothing, so there is none and the address is drawn.
     */
    @Test
    fun `with no command to launch there is nothing to choose between, and the address is shown`() = runTest {
        val controller = FakeController(enabled = true, launchCommand = null)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        assertNull(state().launch)
        assertFalse(state().showsConnectionTabs, "a single way in was offered as a choice")
        assertTrue(state().showsAddress, "the only way to connect was not on screen")
        assertFalse(state().showsCommand, "a command the app cannot name was drawn anyway")
    }

    /**
     * A tab the user asked for that the section cannot honour is not remembered as if it could be.
     *
     * The choice is a field of the state and the command tab is its default, so with no command to
     * launch the section has to answer with what it can draw rather than with what was asked.
     */
    @Test
    fun `with no command to launch, asking for the command still shows the address`() = runTest {
        val controller = FakeController(enabled = true, launchCommand = null)
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        viewModel.onAction(McpAction.SelectConnectionTab(McpConnectionTab.COMMAND))

        assertEquals(McpConnectionTab.ADDRESS, state().selectedConnectionTab)
        assertTrue(state().showsAddress, "the section drew neither of the two")
    }

    // ------------------------------------------------------------------------------
    // What the section stopped saying
    // ------------------------------------------------------------------------------

    /**
     * Two sentences the section used to say are false from here on: that the server exists only
     * while the app is open, and that a client speaking only stdio needs an adapter of its own —
     * which is precisely what the app now is. A string left behind is not a stale comment: it is
     * the app telling the user something untrue, in whichever of the two languages it survives in.
     */
    @Test
    fun `the section no longer says the surface needs an open window`() {
        listOf("values", "values-en").forEach { language ->
            val strings = stringsOf(language)

            listOf("mcp_app_open_note", "mcp_instructions_stdio_note").forEach { key ->
                assertFalse(
                    strings.containsKey(key),
                    "$language/strings.xml still declares $key, which the stdio mode made false",
                )
            }
        }
    }

    @Test
    fun `the connection instructions say the command works with the app closed`() {
        mapOf(
            "values" to listOf("aberto", "fechado"),
            "values-en" to listOf("open", "closed"),
        ).forEach { (language, states) ->
            val note = assertNotNull(
                stringsOf(language)["mcp_command_note"],
                "$language/strings.xml does not say what the command does",
            )

            states.forEach { state ->
                assertTrue(
                    note.contains(state, ignoreCase = true),
                    "the instructions in $language do not say the command works with the app " +
                        "$state: \"$note\"",
                )
            }
        }
    }

    /**
     * Tabs put the two ways in on the same footing, so the text carries the whole difference.
     *
     * Folded away under "advanced", the address was visibly the second choice and its one
     * limitation could ride along in the fold. As a tab it looks exactly like the command, and the
     * only thing left saying that it goes dead with the window is this sentence.
     */
    @Test
    fun `the address tab says it only answers with the app open`() {
        mapOf(
            "values" to "aberto",
            "values-en" to "open",
        ).forEach { (language, open) ->
            val note = assertNotNull(
                stringsOf(language)["mcp_address_note"],
                "$language/strings.xml does not say when the address answers",
            )

            assertTrue(
                note.contains(open, ignoreCase = true),
                "the address tab in $language does not say it needs the app open: \"$note\"",
            )
        }
    }

    /**
     * The label of the one-line form is half a sentence, and the client's name is the other half.
     *
     * It reads "Claude Code, in one line", with the name a menu that rewrites the line under it. So
     * the string is a fragment on purpose, and one tidied into a whole sentence would leave the
     * section saying the name twice — or saying it once, in a place that no longer chooses anything.
     */
    @Test
    fun `the label of the one-line form continues the sentence the client's name opens`() {
        listOf("values", "values-en").forEach { language ->
            val label = assertNotNull(
                stringsOf(language)["mcp_command_line_label"],
                "$language/strings.xml declares no label for the one-line form",
            )

            assertTrue(
                label.startsWith(","),
                "$language/strings.xml made the label a sentence of its own: \"$label\". The " +
                    "client's name is drawn before it and completes it.",
            )
        }
    }

    /** "Advanced" described a fold, and there is no fold: a key left behind names a path that is
     * now a tab like the other one. */
    @Test
    fun `the section no longer offers an advanced path`() {
        listOf("values", "values-en").forEach { language ->
            val strings = stringsOf(language)

            listOf("mcp_advanced_title", "mcp_advanced_body").forEach { key ->
                assertFalse(
                    strings.containsKey(key),
                    "$language/strings.xml still declares $key, which the tabs replaced",
                )
            }
        }
    }

    // ------------------------------------------------------------------------------
    // 13.2d — the log, and what it reaches
    // ------------------------------------------------------------------------------

    @Test
    fun `an entry reaches the posting it describes`() = runTest {
        val activity = FakeActivity(
            listOf(entry(id = 1, operation = "create_transaction", reference = reference(Kind.TRANSACTION, id = 7)))
        )
        val viewModel = viewModelOf(
            FakeController(enabled = true),
            activity,
            FakeTransactions(existing = setOf(7L)),
        )
        val state = subscribe(viewModel)

        val row = state().recentActivity.single()

        assertEquals(McpActivityTarget.Posting(transactionId = 7), row.target)
        assertFalse(row.isTargetGone)
    }

    @Test
    fun `an entry whose posting was removed is still an entry`() = runTest {
        val activity = FakeActivity(
            listOf(entry(id = 1, operation = "create_transaction", reference = reference(Kind.TRANSACTION, id = 7)))
        )
        // Nothing holds the posting in place — the log carries no foreign key, on purpose.
        val viewModel = viewModelOf(
            FakeController(enabled = true),
            activity,
            FakeTransactions(existing = emptySet()),
        )
        val state = subscribe(viewModel)

        val row = state().recentActivity.single()

        assertEquals(1, row.id, "the entry was dropped because what it created is gone")
        assertEquals("create transaction", row.summary, "the testimony was rewritten after the fact")
        assertTrue(row.isTargetGone, "the section still offered a door to a posting that is not there")
    }

    @Test
    fun `a refusal is recorded, and leads nowhere because it changed nothing`() = runTest {
        val activity = FakeActivity(
            listOf(
                entry(
                    id = 2,
                    operation = "delete_category",
                    outcome = AgentActivity.Outcome.REFUSED,
                    detail = "category has transactions",
                    reference = null,
                ),
            )
        )
        val viewModel = viewModelOf(FakeController(enabled = true), activity)
        val state = subscribe(viewModel)

        val row = state().recentActivity.single()

        assertTrue(row.isRefused)
        assertEquals("category has transactions", row.detail, "the refusal did not say why")
        assertNull(row.target)
    }

    @Test
    fun `every kind of reference has somewhere to go`() {
        val targets = Kind.entries.associateWith { kind -> reference(kind, id = 3).toTarget() }

        assertEquals(Kind.entries.size, targets.size)
        assertEquals(
            McpActivityTarget.Posting(transactionId = 3),
            targets.getValue(Kind.TRANSACTION),
            "the posting is the one reference the log exists to let the user check",
        )
        assertEquals(
            McpActivityTarget.Section(AccountsRoute(accountId = 3)),
            targets.getValue(Kind.ACCOUNT),
            "an account is reached by its identity, not by a list to search through",
        )
        assertEquals(
            McpActivityTarget.Section(CreditCardsRoute(creditCardId = 3)),
            targets.getValue(Kind.CREDIT_CARD),
        )
    }

    @Test
    fun `the section shows a glance, and clearing empties the log`() = runTest {
        val activity = FakeActivity(
            (1..20).map { entry(id = it.toLong(), operation = "create_transaction", reference = null) }
        )
        val viewModel = viewModelOf(FakeController(enabled = true), activity)
        val state = subscribe(viewModel)

        assertEquals(
            McpViewModel.SECTION_PREVIEW,
            state().recentActivity.size,
            "the section opened with the whole log instead of a glance at it",
        )

        viewModel.onAction(McpAction.ClearActivity)

        assertTrue(state().recentActivity.isEmpty())
    }

    // ------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------

    /**
     * Subscribes to the state and hands back a reading of it.
     *
     * The state only exists while something is collecting it — `WhileSubscribed` — and the
     * unconfined dispatcher makes every emission land before the next assertion runs.
     */
    private fun TestScope.subscribe(viewModel: McpViewModel): () -> McpUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return { viewModel.uiState.value }
    }

    private fun viewModelOf(
        controller: FakeController,
        activity: FakeActivity = FakeActivity(),
        transactions: FakeTransactions = FakeTransactions(),
    ) = McpViewModel(controller, activity, transactions, ClearAgentActivityUseCase(activity))

    private fun entry(
        id: Long,
        operation: String,
        outcome: AgentActivity.Outcome = AgentActivity.Outcome.APPLIED,
        detail: String? = null,
        reference: AgentActivity.Reference?,
    ) = AgentActivity(
        id = id,
        at = Instant.fromEpochSeconds(1_700_000_000),
        operation = operation,
        summary = operation.replace('_', ' '),
        outcome = outcome,
        detail = detail,
        reference = reference,
    )

    private fun reference(kind: Kind, id: Long) = AgentActivity.Reference(kind = kind, id = id)

    /** The command the entry point recognises, pointed at the executable installed at [path]. */
    private fun installedAt(path: String) = McpLaunchCommand(
        command = path,
        args = listOf(McpLaunchCommand.STDIO_ARGUMENT),
    )

    /** What the app ships as text in one language, read from the file that ships it. */
    private fun stringsOf(language: String): Map<String, String> {
        val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val strings = File(repoRoot, "core/resources/src/commonMain/composeResources/$language/strings.xml")

        return STRING_ENTRY.findAll(strings.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private class FakeController(
        enabled: Boolean = false,
        port: Int = McpServerController.DEFAULT_PORT,
        token: String? = null,
        permissions: Set<McpPermissionAxis> = McpPermissionAxis.INITIAL,
        /** Absent on every target without a process a client could launch. */
        override val launchCommand: McpLaunchCommand? = null,
    ) : McpServerController {

        val calls = mutableListOf<String>()

        val serverState = MutableStateFlow<McpServerState>(McpServerState.Stopped)
        override val state: StateFlow<McpServerState> = serverState

        private val enabledState = MutableStateFlow(enabled)
        override val isEnabled: StateFlow<Boolean> = enabledState

        private val portState = MutableStateFlow(port)
        override val port: StateFlow<Int> = portState

        private val tokenState = MutableStateFlow(token)
        override val token: StateFlow<String?> = tokenState

        private val permissionsState = MutableStateFlow(permissions)
        override val permissions: StateFlow<Set<McpPermissionAxis>> = permissionsState

        override val toolCountByAxis: Map<McpPermissionAxis, Int> = mapOf(
            McpPermissionAxis.READ to 20,
            McpPermissionAxis.RECORD to 15,
            McpPermissionAxis.REMOVE to 8,
            McpPermissionAxis.OPERATE to 13,
        )

        override suspend fun start() = Unit

        override suspend fun stop() = Unit

        override suspend fun setEnabled(enabled: Boolean) {
            calls += "setEnabled($enabled)"
            enabledState.value = enabled
        }

        override suspend fun setPort(port: Int) {
            calls += "setPort($port)"
            portState.value = port
        }

        override suspend fun setPermission(axis: McpPermissionAxis, granted: Boolean) {
            calls += "setPermission($axis, $granted)"
            permissionsState.value = if (granted) {
                permissionsState.value + axis
            } else {
                permissionsState.value - axis
            }
        }

        override suspend fun regenerateToken() {
            calls += "regenerateToken"
            tokenState.value = "minted"
        }

        override suspend fun disconnectSessions() {
            calls += "disconnectSessions"
        }
    }

    private class FakeActivity(initial: List<AgentActivity> = emptyList()) : IAgentActivityRepository {

        private val entries = MutableStateFlow(initial)

        override fun observeRecent(limit: Int): Flow<List<AgentActivity>> = entries.map { it.take(limit) }

        override fun observeAll(): Flow<List<AgentActivity>> = entries

        override suspend fun record(
            operation: String,
            summary: String,
            outcome: AgentActivity.Outcome,
            detail: String?,
            reference: AgentActivity.Reference?,
        ): Long = 0

        override suspend fun clear() {
            entries.value = emptyList()
        }
    }

    /** Answers one question — whether a posting is still there — and refuses to be asked others. */
    private class FakeTransactions(private val existing: Set<Long> = emptySet()) : ITransactionRepository {

        override suspend fun getTransactionById(id: Long): Transaction? =
            if (id in existing) Transaction(id = id, title = null, date = LocalDate(2026, 1, 1)) else null

        override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = ids.intersect(existing)

        override fun observeAllTransactions() = unsupported()

        override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?) = unsupported()

        override fun observeTransactionById(id: Long) = unsupported()

        override suspend fun getAllTransactions() = unsupported()

        override suspend fun getTransactionsBetween(startDate: LocalDate, endDate: LocalDate) =
            unsupported()

        override suspend fun createTransaction(intent: TransactionIntent) = unsupported()

        override suspend fun createTransactions(intents: List<TransactionIntent>) = unsupported()

        override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =

            throw NotImplementedError()


        override suspend fun updateTransaction(
            id: Long,
            title: String?,
            date: LocalDate,
            legs: List<TransactionLeg>,
            contra: ContraLeg?,
        ) = unsupported()

        override suspend fun deleteTransactionById(id: Long) = unsupported()

        override suspend fun deleteTransactionsByIds(ids: List<Long>) = unsupported()

        private fun unsupported(): Nothing =
            error("The section reads the ledger for one thing only: whether a posting still exists.")
    }
}

private typealias Kind = AgentActivity.Reference.Kind

/** Where the packaged launcher lives on macOS: the path `jpackage.app-path` reports there. */
private const val MACOS = "/Applications/Finsight.app/Contents/MacOS/Finsight"

/** And on Windows, where the separator is the character JSON escapes. */
private const val WINDOWS = """C:\Users\ana\AppData\Local\Finsight\Finsight.exe"""

private val STRING_ENTRY = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
