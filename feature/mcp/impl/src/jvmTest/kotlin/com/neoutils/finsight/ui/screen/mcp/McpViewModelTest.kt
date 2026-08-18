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
        val controller = FakeController()
        val viewModel = viewModelOf(controller)
        val state = subscribe(viewModel)

        assertFalse(
            state().showsDetails,
            "the section offered the address, the token or the permissions before there was a " +
                "server to connect to",
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

    private class FakeController(
        enabled: Boolean = false,
        port: Int = McpServerController.DEFAULT_PORT,
        token: String? = null,
        permissions: Set<McpPermissionAxis> = McpPermissionAxis.INITIAL,
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

        override suspend fun updateTransaction(
            id: Long,
            title: String?,
            date: LocalDate,
            leg: TransactionLeg,
            contra: ContraLeg?,
        ) = unsupported()

        override suspend fun deleteTransactionById(id: Long) = unsupported()

        override suspend fun deleteTransactionsByIds(ids: List<Long>) = unsupported()

        private fun unsupported(): Nothing =
            error("The section reads the ledger for one thing only: whether a posting still exists.")
    }
}

private typealias Kind = AgentActivity.Reference.Kind
