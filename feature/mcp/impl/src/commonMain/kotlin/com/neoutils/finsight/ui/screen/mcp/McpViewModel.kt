@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerStateSource
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerSettings
import com.neoutils.finsight.feature.mcp.api.McpServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * The MCP server screen's state, and the only place this screen decides anything.
 *
 * The three states come from **the server**, not from the configuration: *enabled* says what the
 * user asked for, and only the running server knows whether anything is listening. A port already
 * taken is the case where the two answers differ, and it is the reason the screen has a third
 * state at all.
 *
 * The connection snippet is assembled here, from the endpoint the server reports and the token in
 * force, so it follows both without the screen recomputing anything: rotate the token and the
 * snippet on screen is already the new one.
 */
class McpViewModel(
    private val settingsRepository: IMcpServerSettingsRepository,
    activityRepository: IAgentActivityRepository,
    serverState: IMcpServerStateSource,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    // Purely a matter of what is on screen: nothing here is persisted, and hiding the token again
    // is not a security event — showing it was the deliberate act.
    private val isTokenVisible = MutableStateFlow(false)

    val uiState = combine(
        settingsRepository.observe(),
        serverState.state,
        activityRepository.observeRecent(RECENT_ACTIVITY_LIMIT),
        isTokenVisible,
        ::stateOf,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = McpUiState.Off(isEnabled = settingsRepository.observe().value.isEnabled),
    )

    fun onAction(action: McpAction) {
        when (action) {
            is McpAction.SetEnabled -> viewModelScope.launch {
                settingsRepository.setEnabled(action.isEnabled)
            }

            is McpAction.SetPermission -> viewModelScope.launch {
                settingsRepository.setPermission(action.permission)
            }

            is McpAction.SetPort -> viewModelScope.launch {
                settingsRepository.setPort(action.port)
            }

            McpAction.RotateToken -> viewModelScope.launch {
                settingsRepository.rotateToken()
            }

            McpAction.ToggleTokenVisibility -> isTokenVisible.value = !isTokenVisible.value
        }
    }

    private fun stateOf(
        settings: McpServerSettings,
        server: McpServerState,
        activity: List<AgentActivity>,
        isTokenVisible: Boolean,
    ): McpUiState = when (server) {
        McpServerState.Stopped -> McpUiState.Off(isEnabled = settings.isEnabled)

        is McpServerState.PortUnavailable -> McpUiState.PortUnavailable(
            port = server.port,
            reason = server.reason,
        )

        is McpServerState.Listening -> McpUiState.Listening(
            endpoint = server.url,
            // The level the *server* is applying, and not the one just persisted: between the two
            // the announced tool list has not changed yet, and the screen would be promising a
            // reach the agent does not have.
            permission = server.permission,
            token = settings.token,
            isTokenVisible = isTokenVisible,
            clientConfig = clientConfigOf(server.url, settings.token),
            protocolRevision = server.protocolRevision,
            isReadOnly = server.permission == McpPermission.READ_ONLY,
            activity = activity.map(::activityOf),
        )
    }

    private fun activityOf(record: AgentActivity) = AgentActivityUi(
        id = record.id,
        timestamp = record.timestamp.toLocalDateTime(timeZone).let { at ->
            "${at.date} ${at.hour.pad()}:${at.minute.pad()}"
        },
        client = record.client,
        tool = record.tool,
        outcome = record.outcome,
        // Only what the interface can open becomes a destination. An identifier of a kind no
        // screen renders leads nowhere, and a dead link is worse than a line that is only a line.
        target = record.affected.firstNotNullOfOrNull(::targetOf),
    )

    private fun targetOf(affected: String): AgentActivityTarget? {
        val (kind, id) = affected.split(':', limit = 2).takeIf { it.size == 2 } ?: return null

        return when (kind) {
            TRANSACTION_KIND -> id.toLongOrNull()?.let(AgentActivityTarget::Transaction)
            else -> null
        }
    }

    private fun Int.pad() = toString().padStart(length = 2, padChar = '0')

    companion object {

        /**
         * How many records the screen follows. The journal is pruned by its retention policy, not
         * by this number: what is on screen is the recent activity, never the whole history.
         */
        const val RECENT_ACTIVITY_LIMIT = 50

        /**
         * The one kind of identifier the journal carries that the interface can open today.
         *
         * It is the shape the write tools record (`transaction:<id>`), and it is matched rather
         * than assumed: a line whose identifier does not parse simply does not lead anywhere.
         */
        private const val TRANSACTION_KIND = "transaction"

        /**
         * The configuration a client is pasted, with the address and the token already in place.
         *
         * It is **the file format MCP clients read**, and not a rendering of the two values side
         * by side: a user who has to assemble the format themselves is exactly the case the
         * requirement excludes. Nothing here is translated — it is a configuration file, and its
         * keys are the protocol's.
         */
        fun clientConfigOf(endpoint: String, token: String): String = """
            {
              "mcpServers": {
                "finsight": {
                  "type": "http",
                  "url": "$endpoint",
                  "headers": {
                    "Authorization": "Bearer $token"
                  }
                }
              }
            }
        """.trimIndent()
    }
}
