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
 * the address and token a client is configured with, and what an agent has been doing.
 *
 * **The state is collected, never inferred.** Everything about the socket comes from
 * [McpServerController.state] for as long as the screen is subscribed, which is what lets the
 * section show a server that was switched on and did not come up — and keeps it from ever showing
 * "up" for a socket that is down.
 *
 * **The port is typed here and applied on purpose.** What the user types is a draft: it is
 * validated on the field, and the server is only rebound when they say so. While no draft exists
 * the field follows the port the server holds, so a port changed anywhere else is reflected without
 * fighting the user's typing.
 */
class McpViewModel(
    private val controller: McpServerController,
    activityRepository: IAgentActivityRepository,
    private val transactionRepository: ITransactionRepository,
    private val clearAgentActivity: ClearAgentActivityUseCase,
) : ViewModel() {

    /** `null` while the field simply follows the server's port — the user has typed nothing. */
    private val portDraft = MutableStateFlow<String?>(null)

    private val isTokenRevealed = MutableStateFlow(false)

    private val fieldState = combine(
        controller.port,
        controller.token,
        portDraft,
        isTokenRevealed,
    ) { port, token, draft, revealed ->
        FieldState(port = port, token = token, draft = draft ?: port.toString(), revealed = revealed)
    }

    private val recentActivity = activityRepository
        .observeRecent(SECTION_PREVIEW)
        .mapLatest { entries -> entries.map { it.toUi(transactionRepository) } }

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
            portDraft = fields.draft,
            token = fields.token,
            isTokenRevealed = fields.revealed,
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

            is McpAction.EditPort -> portDraft.value = action.text.filter { it.isDigit() }.take(PORT_DIGITS)

            McpAction.ApplyPort -> {
                // The same rule the field states, read from the state that states it: a second
                // definition of "a port" here would be one edit away from letting through exactly
                // what the field is refusing on screen.
                val port = uiState.value.takeIf { it.canApplyPort }?.portDraft?.toInt() ?: return
                viewModelScope.launch {
                    controller.setPort(port)
                    // The draft has become the port: the field goes back to following the server,
                    // so a later change from anywhere else reaches it.
                    portDraft.value = null
                }
            }

            McpAction.ToggleTokenVisibility -> isTokenRevealed.value = !isTokenRevealed.value

            McpAction.RegenerateToken -> viewModelScope.launch { controller.regenerateToken() }

            McpAction.DisconnectSessions -> viewModelScope.launch { controller.disconnectSessions() }

            McpAction.ClearActivity -> viewModelScope.launch { clearAgentActivity() }
        }
    }

    private data class FieldState(
        val port: Int,
        val token: String?,
        val draft: String,
        val revealed: Boolean,
    )

    companion object {

        /**
         * How many acts the section shows without being asked. A glance, not the log: the whole of
         * it is one step away, and a section that opened with fifty rows would bury everything
         * above it.
         */
        const val SECTION_PREVIEW = 5

        /** 65535 is five digits; anything longer is not a port being typed. */
        private const val PORT_DIGITS = 5
    }
}
