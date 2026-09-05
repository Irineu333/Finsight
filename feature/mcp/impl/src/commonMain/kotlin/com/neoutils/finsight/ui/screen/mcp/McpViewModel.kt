@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ClearAgentActivityUseCase
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.shell.api.FeaturePlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The MCP server section: the switch, what the socket is actually doing, the four permission axes,
 * the two ways a client reaches the app — the command it launches and the address it is pointed at,
 * offered side by side — and what an agent has been doing.
 *
 * **The state is collected, never inferred.** Everything about the socket comes from
 * [McpServerController.state] for as long as the screen is subscribed, which is what lets the
 * section show a server that was switched on and did not come up — and keeps it from ever showing
 * "up" for a socket that is down.
 *
 * **The port arrives already chosen.** Collecting and validating it belongs to the sheet that asks
 * for it; what reaches here is a port the user settled on, and the server is rebound once. The
 * range is checked again all the same, against the contract's own — a rule stated in two places
 * would be one edit away from disagreeing with itself.
 */
class McpViewModel(
    private val controller: McpServerController,
    activityRepository: IAgentActivityRepository,
    private val transactionRepository: ITransactionRepository,
    private val clearAgentActivity: ClearAgentActivityUseCase,
) : ViewModel() {

    private val isTokenRevealed = MutableStateFlow(false)

    private val connectionTab = MutableStateFlow(McpConnectionTab.COMMAND)

    private val commandTarget = MutableStateFlow(McpCommandTarget.CLAUDE_CODE)

    private val fieldState = combine(
        controller.port,
        controller.token,
        isTokenRevealed,
        connectionTab,
        commandTarget,
    ) { port, token, revealed, tab, target ->
        FieldState(
            port = port,
            token = token,
            revealed = revealed,
            connectionTab = tab,
            commandTarget = target,
        )
    }

    private val recentActivity = activityRepository
        .observeRecent(SECTION_PREVIEW)
        .mapLatest { entries -> entries.toUi(transactionRepository) }

    val uiState = combine(
        controller.state,
        controller.isEnabled,
        controller.permissions,
        fieldState,
        recentActivity,
    ) { server, isEnabled, granted, fields, activity ->
        McpUiState(
            isSupported = FeaturePlatform.DESKTOP.isCurrent,
            isEnabled = isEnabled,
            server = server,
            port = fields.port,
            token = fields.token,
            isTokenRevealed = fields.revealed,
            // What the process was launched from cannot change while it runs, so it is read from
            // the controller rather than collected — and it is the controller's to answer, beside
            // the port and the token, so that no screen goes looking for it in the system.
            launchCommand = controller.launchCommand,
            connectionTab = fields.connectionTab,
            commandTarget = fields.commandTarget,
            permissions = McpPermissionAxis.entries.map { axis ->
                McpPermissionUi(
                    axis = axis,
                    isGranted = axis in granted,
                    // The one place the surface is counted, shared with what the socket announces:
                    // the number the user reads cannot drift from the number the agent gets.
                    toolCount = controller.toolCountByAxis[axis] ?: 0,
                )
            },
            recentActivity = activity,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = McpUiState(isSupported = FeaturePlatform.DESKTOP.isCurrent),
    )

    fun onAction(action: McpAction) {
        when (action) {
            is McpAction.SetEnabled -> viewModelScope.launch { controller.setEnabled(action.enabled) }

            is McpAction.SetPermission -> viewModelScope.launch {
                controller.setPermission(action.axis, action.granted)
            }

            // The range is the contract's, not a second definition here: what the sheet refuses
            // to collect and what the controller accepts are the same rule, so a caller reaching
            // this from elsewhere cannot let through what the sheet is refusing on screen.
            is McpAction.ChangePort -> {
                if (action.port !in McpServerController.VALID_PORTS) return
                viewModelScope.launch { controller.setPort(action.port) }
            }

            McpAction.ToggleTokenVisibility -> isTokenRevealed.value = !isTokenRevealed.value

            is McpAction.SelectConnectionTab -> connectionTab.value = action.tab

            is McpAction.SelectCommandTarget -> commandTarget.value = action.target

            McpAction.RegenerateToken -> viewModelScope.launch { controller.regenerateToken() }

            McpAction.DisconnectSessions -> viewModelScope.launch { controller.disconnectSessions() }

            McpAction.ClearActivity -> viewModelScope.launch { clearAgentActivity() }
        }
    }

    private data class FieldState(
        val port: Int,
        val token: String?,
        val revealed: Boolean,
        val connectionTab: McpConnectionTab,
        val commandTarget: McpCommandTarget,
    )

    companion object {

        /**
         * How many acts the section shows without being asked. A glance, not the log: the whole of
         * it is one step away, and a section that opened with fifty rows would bury everything
         * above it.
         */
        const val SECTION_PREVIEW = 5
    }
}
