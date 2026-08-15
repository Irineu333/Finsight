@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.mcp

import app.cash.turbine.test
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerStateSource
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerSettings
import com.neoutils.finsight.feature.mcp.api.McpServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The three states the screen has to tell apart, and the one thing each of them must not do.
 *
 * They are asserted on the state and not on the rendering because the state is where the decision
 * lives: a screen that read the same state and drew the wrong thing would be a bug in one place,
 * while a decision taken in the composable would be a bug with no place to test it.
 */
class McpViewModelTest {

    private val token = "0123456789abcdef0123456789abcdef"

    private val settings = FakeSettings()
    private val journal = FakeJournal()
    private val server = FakeServerState()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `off offers no connection snippet, because nothing is listening`() = runTest {
        viewModel().uiState.test {
            val state = assertIs<McpUiState.Off>(awaitItem())

            assertFalse(state.isEnabled)
        }
    }

    @Test
    fun `on offers address, level, token hidden, snippet and activity, all at once`() = runTest {
        journal.records.value = listOf(
            AgentActivity(
                id = 1,
                timestamp = Instant.fromEpochSeconds(1_770_000_000),
                client = "some-agent",
                tool = "finsight_record_transactions",
                arguments = "{}",
                outcome = AgentActivityOutcome.OK,
                affected = listOf("transaction:77"),
            ),
        )
        settings.state.value = settings.state.value.copy(isEnabled = true)
        server.state.value = listening(McpPermission.READ_ONLY)

        viewModel().uiState.test {
            val state = assertIs<McpUiState.Listening>(awaitItem())

            assertEquals("http://127.0.0.1:8765/mcp", state.endpoint)
            assertEquals(McpPermission.READ_ONLY, state.permission)
            assertFalse(state.isTokenVisible, "the token is shown in clear by default")
            assertTrue(state.isReadOnly, "the instructions would not warn that no write is visible")
            assertEquals("2025-11-25", state.protocolRevision)

            // Ready to paste: the address and the token are already in place.
            assertContains(state.clientConfig, "http://127.0.0.1:8765/mcp")
            assertContains(state.clientConfig, "Bearer $token")

            val line = state.activity.single()
            assertEquals("some-agent", line.client)
            assertEquals(AgentActivityTarget.Transaction(77), line.target)
        }
    }

    @Test
    fun `an occupied port is neither on nor off, and names the conflict`() = runTest {
        settings.state.value = settings.state.value.copy(isEnabled = true)
        server.state.value = McpServerState.PortUnavailable(port = 8765, reason = "Address already in use")

        viewModel().uiState.test {
            val state = assertIs<McpUiState.PortUnavailable>(awaitItem())

            assertEquals(8765, state.port)
            assertEquals("Address already in use", state.reason)
        }
    }

    @Test
    fun `revealing the token changes what is on screen and nothing that is persisted`() = runTest {
        settings.state.value = settings.state.value.copy(isEnabled = true)
        server.state.value = listening(McpPermission.READ_WRITE)
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertFalse(assertIs<McpUiState.Listening>(awaitItem()).isTokenVisible)

            viewModel.onAction(McpAction.ToggleTokenVisibility)

            assertTrue(assertIs<McpUiState.Listening>(awaitItem()).isTokenVisible)
            assertEquals(token, settings.state.value.token, "revealing rotated the token")
        }
    }

    @Test
    fun `rotating the token carries into the snippet the user pastes`() = runTest {
        settings.state.value = settings.state.value.copy(isEnabled = true)
        server.state.value = listening(McpPermission.READ_ONLY)
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertContains(assertIs<McpUiState.Listening>(awaitItem()).clientConfig, "Bearer $token")

            viewModel.onAction(McpAction.RotateToken)

            val rotated = assertIs<McpUiState.Listening>(awaitItem())
            assertContains(rotated.clientConfig, "Bearer ${settings.state.value.token}")
            assertFalse(rotated.clientConfig.contains(token), "the revoked token is still offered")
        }
    }

    @Test
    fun `a record that touched nothing the interface opens leads nowhere`() = runTest {
        journal.records.value = listOf(
            AgentActivity(
                id = 2,
                timestamp = Instant.fromEpochSeconds(1_770_000_000),
                client = null,
                tool = "finsight_record_transactions",
                arguments = "{}",
                outcome = AgentActivityOutcome.REFUSED,
                affected = emptyList(),
            ),
        )
        settings.state.value = settings.state.value.copy(isEnabled = true)
        server.state.value = listening(McpPermission.READ_WRITE)

        viewModel().uiState.test {
            val line = assertIs<McpUiState.Listening>(awaitItem()).activity.single()

            assertNull(line.target)
            assertNull(line.client, "a client that did not introduce itself is not invented")
            assertEquals(AgentActivityOutcome.REFUSED, line.outcome)
        }
    }

    private fun viewModel() = McpViewModel(
        settingsRepository = settings,
        activityRepository = journal,
        serverState = server,
        timeZone = TimeZone.UTC,
    )

    private fun listening(permission: McpPermission) = McpServerState.Listening(
        url = "http://127.0.0.1:8765/mcp",
        permission = permission,
        protocolRevision = "2025-11-25",
    )

    private inner class FakeSettings : IMcpServerSettingsRepository {
        val state = MutableStateFlow(
            McpServerSettings(
                isEnabled = false,
                permission = McpPermission.READ_ONLY,
                port = 8765,
                token = token,
            ),
        )

        override fun observe(): StateFlow<McpServerSettings> = state

        override suspend fun setEnabled(isEnabled: Boolean) {
            state.value = state.value.copy(isEnabled = isEnabled)
        }

        override suspend fun setPermission(permission: McpPermission) {
            state.value = state.value.copy(permission = permission)
        }

        override suspend fun setPort(port: Int) {
            state.value = state.value.copy(port = port)
        }

        override suspend fun rotateToken(): String {
            val rotated = "rotated-${state.value.token.length}"
            state.value = state.value.copy(token = rotated)
            return rotated
        }
    }

    private class FakeJournal : IAgentActivityRepository {
        val records = MutableStateFlow(emptyList<AgentActivity>())

        override fun observeRecent(limit: Int): Flow<List<AgentActivity>> = records

        override suspend fun record(activity: AgentActivity) {
            records.value = listOf(activity) + records.value
        }

        override suspend fun prune(olderThan: Instant) = Unit
    }

    private class FakeServerState : IMcpServerStateSource {
        override val state = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    }
}
