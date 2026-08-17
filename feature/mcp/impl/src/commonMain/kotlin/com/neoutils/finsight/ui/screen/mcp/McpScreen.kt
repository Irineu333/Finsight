@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.neoutils.finsight.ui.component.FinsightSwitch
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.editPort.EditPortModal
import com.neoutils.finsight.ui.modal.regenerateToken.RegenerateTokenModal
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_about_body
import com.neoutils.finsight.resources.mcp_activity_see_all
import com.neoutils.finsight.resources.mcp_activity_title
import com.neoutils.finsight.resources.mcp_address_label
import com.neoutils.finsight.resources.mcp_app_open_note
import com.neoutils.finsight.resources.mcp_copy
import com.neoutils.finsight.resources.mcp_disconnect_sessions
import com.neoutils.finsight.resources.mcp_enable_description
import com.neoutils.finsight.resources.mcp_enable_title
import com.neoutils.finsight.resources.mcp_instructions_body
import com.neoutils.finsight.resources.mcp_instructions_copy
import com.neoutils.finsight.resources.mcp_instructions_json_note
import com.neoutils.finsight.resources.mcp_instructions_stdio_note
import com.neoutils.finsight.resources.mcp_instructions_title
import com.neoutils.finsight.resources.mcp_permission_granted_count
import com.neoutils.finsight.resources.mcp_permission_operate_description
import com.neoutils.finsight.resources.mcp_permission_operate_title
import com.neoutils.finsight.resources.mcp_permission_read_description
import com.neoutils.finsight.resources.mcp_permission_read_title
import com.neoutils.finsight.resources.mcp_permission_record_description
import com.neoutils.finsight.resources.mcp_permission_record_title
import com.neoutils.finsight.resources.mcp_permission_remove_description
import com.neoutils.finsight.resources.mcp_permission_remove_title
import com.neoutils.finsight.resources.mcp_permission_withheld_count
import com.neoutils.finsight.resources.mcp_permissions_description
import com.neoutils.finsight.resources.mcp_permissions_title
import com.neoutils.finsight.resources.mcp_port_title
import com.neoutils.finsight.resources.mcp_screen_title
import com.neoutils.finsight.resources.mcp_status_clients
import com.neoutils.finsight.resources.mcp_status_failed
import com.neoutils.finsight.resources.mcp_status_no_client
import com.neoutils.finsight.resources.mcp_status_running
import com.neoutils.finsight.resources.mcp_status_stopped
import com.neoutils.finsight.resources.mcp_token_hide
import com.neoutils.finsight.resources.mcp_token_label
import com.neoutils.finsight.resources.mcp_token_pending
import com.neoutils.finsight.resources.mcp_token_regenerate
import com.neoutils.finsight.resources.mcp_token_reveal
import com.neoutils.finsight.resources.mcp_unavailable_description
import com.neoutils.finsight.resources.mcp_unavailable_title
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The MCP server section: what the server is, the switch that turns it on, and — **only then** —
 * everything a client needs to reach it.
 *
 * The order is the requirement. With the server off there is nothing to point a client at, so the
 * address, the token, the permission axes and the instructions are simply not here: switching it on
 * takes no other decision first. Turning it on is what makes them appear.
 *
 * What the section says about the socket comes from the controller's own state, which it collects
 * for as long as it is open. That is what lets it show a server that was switched on and did not
 * come up, instead of reporting the switch back to the user as if it were the socket.
 */
@Composable
internal fun McpScreen(
    onNavigateBack: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    viewModel: McpViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag("screen_mcp"),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.mcp_screen_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        // A list, and keyed, for the sake of the switch: what it turns on is four cards arriving at
        // once, and a list is what carries them in and out — each one fading where it belongs while
        // the cards around it slide to make the room. The same cards in a plain column would simply
        // be there, and the eye would have to find the section it was reading again.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.isSupported) {
                item(key = "unavailable") {
                    UnavailableCard(modifier = Modifier.animateItem())
                }
                return@LazyColumn
            }

            item(key = "about") {
                AboutCard(modifier = Modifier.animateItem())
            }

            item(key = "enable") {
                EnableCard(
                    isEnabled = uiState.isEnabled,
                    onToggle = { viewModel.onAction(McpAction.SetEnabled(it)) },
                    modifier = Modifier.animateItem(),
                )
            }

            if (uiState.showsDetails) {
                item(key = "status") {
                    StatusCard(
                        uiState = uiState,
                        onDisconnect = { viewModel.onAction(McpAction.DisconnectSessions) },
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "connection") {
                    ConnectionCard(
                        uiState = uiState,
                        onAction = viewModel::onAction,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "permissions") {
                    PermissionsCard(
                        permissions = uiState.permissions,
                        onGrant = { axis, granted ->
                            viewModel.onAction(McpAction.SetPermission(axis, granted))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // The log outlives the switch: a server turned off does not erase what an agent did
            // while it was on, and reaching that record is the only place authorship appears. On a
            // section that never ran a server there is nothing to show, which is what keeps the
            // first visit down to what the server is and the switch that starts it.
            if (uiState.showsDetails || uiState.recentActivity.isNotEmpty()) {
                item(key = "activity") {
                    McpActivityCard(
                        entries = uiState.recentActivity,
                        onOpenActivity = onOpenActivity,
                        onClear = { viewModel.onAction(McpAction.ClearActivity) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnavailableCard(
    modifier: Modifier = Modifier,
) = SectionCard(
    title = stringResource(Res.string.mcp_unavailable_title),
    modifier = modifier,
) {
    Text(
        text = stringResource(Res.string.mcp_unavailable_description),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
    )
}

/**
 * What the server is, and the one thing about it that no configuration can change: it is this
 * process. Close the app and the surface is not merely unreachable — it does not exist.
 */
@Composable
private fun AboutCard(
    modifier: Modifier = Modifier,
) = SectionCard(modifier = modifier) {
    Text(
        text = stringResource(Res.string.mcp_about_body),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(Res.string.mcp_app_open_note),
        modifier = Modifier.testTag("mcp_app_open_note"),
        style = typography.bodyMedium,
        color = colorScheme.onSurface,
    )
}

@Composable
private fun EnableCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SectionCard(modifier = modifier) {
    SwitchRow(
        title = stringResource(Res.string.mcp_enable_title),
        description = stringResource(Res.string.mcp_enable_description),
        checked = isEnabled,
        onCheckedChange = onToggle,
        testTag = "mcp_enable_switch",
    )
}

/**
 * What the socket is doing, and — separately — whether anyone is on the other side.
 *
 * The two are different facts and are said as two lines. Only the second one means something may be
 * reading the finances right now, and it is the one that comes with a way to end it.
 */
@Composable
private fun StatusCard(
    uiState: McpUiState,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) = SectionCard(modifier = modifier) {
    val server = uiState.server

    Text(
        text = when (server) {
            is McpServerState.Running -> stringResource(Res.string.mcp_status_running, server.port)
            is McpServerState.Failed -> stringResource(Res.string.mcp_status_failed)
            McpServerState.Stopped -> stringResource(Res.string.mcp_status_stopped)
        },
        modifier = Modifier.testTag("mcp_status"),
        style = typography.titleMedium,
        color = if (uiState.isRunning) colorScheme.onSurface else colorScheme.error,
    )

    if (uiState.isRunning) {
        Text(
            text = if (uiState.hasConnectedClient) {
                stringResource(Res.string.mcp_status_clients, uiState.sessions)
            } else {
                stringResource(Res.string.mcp_status_no_client)
            },
            modifier = Modifier.testTag("mcp_sessions"),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )

        if (uiState.hasConnectedClient) {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.testTag("mcp_disconnect_sessions"),
            ) {
                Text(text = stringResource(Res.string.mcp_disconnect_sessions))
            }
        }
    }
}

/**
 * The port, the address, the token and how to point a client at them.
 *
 * The instructions name no client on purpose: the server speaks the protocol, and whatever speaks
 * it connects. The one thing worth warning about is the transport — this is HTTP on loopback, so a
 * client that only speaks stdio needs an adapter of its own.
 */
@Composable
private fun ConnectionCard(
    uiState: McpUiState,
    onAction: (McpAction) -> Unit,
    modifier: Modifier = Modifier,
) = SectionCard(
    title = stringResource(Res.string.mcp_instructions_title),
    modifier = modifier,
) {
    Text(
        text = stringResource(Res.string.mcp_instructions_body),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
    )

    AddressRow(uiState = uiState, onAction = onAction)

    TokenRow(uiState = uiState, onAction = onAction)

    Text(
        text = stringResource(Res.string.mcp_instructions_stdio_note),
        modifier = Modifier.testTag("mcp_stdio_note"),
        style = typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
    )

    val copy = rememberCopy()
    val instructions = connectionSnippet(address = uiState.address, token = uiState.token)

    Text(
        text = stringResource(Res.string.mcp_instructions_json_note),
        modifier = Modifier.testTag("mcp_instructions_json_note"),
        style = typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
    )

    // Shown and not merely copyable: a block the user can read is one they can adapt when their
    // client words the transport differently, and the values above are what they adapt it from.
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHighest),
        ) {
            // Selectable, because copying the whole block is the common case and not the only
            // one: a user pointing a client that words the transport differently needs the url
            // or the token out of here on its own.
            SelectionContainer {
                Text(
                    text = instructions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        // The end padding is the copy button's room: the block scrolls under a
                        // button that does not, so without it the longest line ends beneath the
                        // icon.
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 48.dp)
                        .testTag("mcp_instructions_json"),
                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }

        IconButton(
            onClick = { copy(instructions) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("mcp_copy_instructions"),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(Res.string.mcp_instructions_copy),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The address a client is pointed at, and the one failure the user can do something about.
 *
 * The port lives inside the address, so changing it starts here rather than in a field of its own:
 * what the user is moving is where a client connects. A port another program is holding is said on
 * this row for the same reason — the affordance to move it is the button beside the message, and a
 * notice elsewhere would state a problem and leave the fix a search away.
 *
 * The address is still shown while a bind is failing: it is the address a client was configured
 * with, and hiding it would take away what the user needs to compare against.
 */
@Composable
private fun AddressRow(
    uiState: McpUiState,
    onAction: (McpAction) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val error = uiState.addressError

    CopyableRow(
        label = stringResource(Res.string.mcp_address_label),
        value = uiState.address,
        copied = uiState.address,
        testTag = "mcp_address",
        leading = {
            RowIconButton(
                icon = Icons.Default.Edit,
                contentDescription = stringResource(Res.string.mcp_port_title),
                onClick = {
                    modalManager.show(
                        EditPortModal(
                            current = uiState.port,
                            onConfirm = { onAction(McpAction.ChangePort(it)) },
                        )
                    )
                },
                modifier = Modifier.testTag("mcp_address_edit_port"),
            )
        },
    )

    if (error != null) {
        Text(
            text = stringUiText(error),
            modifier = Modifier.testTag("mcp_address_error"),
            style = typography.bodySmall,
            color = colorScheme.error,
        )
    }
}

/**
 * The token, hidden until it is asked for.
 *
 * A section that shows it is a target in a screenshot or a shared screen, and the user almost never
 * needs to *read* it — copying is what configuring a client takes.
 */
@Composable
private fun TokenRow(
    uiState: McpUiState,
    onAction: (McpAction) -> Unit,
) {
    val token = uiState.displayedToken

    if (token == null) {
        Text(
            text = stringResource(Res.string.mcp_token_pending),
            modifier = Modifier.testTag("mcp_token_pending"),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        return
    }

    val modalManager = LocalModalManager.current

    CopyableRow(
        label = stringResource(Res.string.mcp_token_label),
        value = token,
        copied = uiState.token.orEmpty(),
        testTag = "mcp_token",
        leading = {
            RowIconButton(
                icon = if (uiState.isTokenRevealed) {
                    Icons.Default.VisibilityOff
                } else {
                    Icons.Default.Visibility
                },
                contentDescription = stringResource(
                    if (uiState.isTokenRevealed) Res.string.mcp_token_hide else Res.string.mcp_token_reveal
                ),
                onClick = { onAction(McpAction.ToggleTokenVisibility) },
                modifier = Modifier.testTag("mcp_token_visibility"),
            )

            // Beside revealing and copying because all three are about this token, and behind a
            // confirmation because only this one takes something away: the other two leave every
            // configured client working, and this one stops them all.
            RowIconButton(
                icon = Icons.Default.Autorenew,
                contentDescription = stringResource(Res.string.mcp_token_regenerate),
                onClick = {
                    modalManager.show(
                        RegenerateTokenModal(onConfirm = { onAction(McpAction.RegenerateToken) })
                    )
                },
                modifier = Modifier.testTag("mcp_token_regenerate"),
            )
        },
    )
}

/** A value the user has to give a client, with the only affordance that avoids transcribing it. */
@Composable
private fun CopyableRow(
    label: String,
    value: String,
    copied: String,
    testTag: String,
    leading: @Composable (() -> Unit)? = null,
) {
    val copy = rememberCopy()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                modifier = Modifier.testTag("${testTag}_value"),
                style = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        leading?.invoke()

        RowIconButton(
            icon = Icons.Default.ContentCopy,
            contentDescription = stringResource(Res.string.mcp_copy),
            onClick = { copy(copied) },
            modifier = Modifier.testTag("${testTag}_copy"),
        )
    }
}

/**
 * A row's affordance: the icon, drawn smaller than the button that carries it.
 *
 * A row here holds up to three of them beside a value that has to stay readable, so the icon is
 * sized down to leave room around it while the button keeps the touch target it is entitled to.
 */
@Composable
private fun RowIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The four axes, each saying what flipping it does.
 *
 * The count is not decoration: a switch whose effect is not stated is granted blind, so a granted
 * axis says how many tools it hands over and a withheld one says how many it is holding back.
 */
@Composable
private fun PermissionsCard(
    permissions: List<McpPermissionUi>,
    onGrant: (McpPermissionAxis, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = SectionCard(
    title = stringResource(Res.string.mcp_permissions_title),
    modifier = modifier,
) {
    Text(
        text = stringResource(Res.string.mcp_permissions_description),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
    )

    permissions.forEachIndexed { index, permission ->
        if (index > 0) HorizontalDivider(color = colorScheme.outlineVariant)

        SwitchRow(
            title = stringResource(permission.axis.titleRes),
            description = stringResource(permission.axis.descriptionRes),
            effect = if (permission.isGranted) {
                stringResource(Res.string.mcp_permission_granted_count, permission.toolCount)
            } else {
                stringResource(Res.string.mcp_permission_withheld_count, permission.toolCount)
            },
            checked = permission.isGranted,
            onCheckedChange = { onGrant(permission.axis, it) },
            testTag = "mcp_permission_${permission.axis.key}",
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    effect: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = typography.titleSmall, color = colorScheme.onSurface)
            Text(
                text = description,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            if (effect != null) {
                Text(
                    text = effect,
                    modifier = Modifier.testTag("${testTag}_effect"),
                    style = typography.labelMedium,
                    color = colorScheme.primary,
                )
            }
        }

        RowGap()

        FinsightSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("${testTag}_switch"),
        )
    }
}

/** The gap between what a row says and the control that acts on it. */
@Composable
private fun RowGap() = Spacer(modifier = Modifier.width(12.dp))

/** The one card shape this section is built out of, so a card added later takes the same look. */
@Composable
private fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Text(text = title, style = typography.titleMedium, color = colorScheme.onSurface)
            }
            content()
        }
    }
}

/**
 * Putting a value on the clipboard — the whole point of showing an address and a token that nobody
 * should be transcribing by hand.
 *
 * It goes through the deprecated [LocalClipboardManager] deliberately. Its replacement takes a
 * `ClipEntry`, which is an `expect class` with no common way to build one, so reaching it from
 * `commonMain` would take an `expect`/`actual` of our own on three targets to write a line of text.
 */
@Suppress("DEPRECATION")
@Composable
private fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { text -> clipboard.setText(AnnotatedString(text)) }
}

/**
 * What a client has to be told, in the vocabulary of the protocol rather than of one client.
 *
 * Every line here is a term any MCP client uses — an address, a transport and an authorisation
 * header — so pasting it into whichever configuration file a client happens to have is a matter of
 * putting each value where that client keeps it.
 */
private fun connectionSnippet(address: String, token: String?): String = """
    {
      "mcpServers": {
        "finsight": {
          "type": "http",
          "url": "$address",
          "headers": {
            "Authorization": "Bearer ${token.orEmpty()}"
          }
        }
      }
    }
""".trimIndent()

private val McpPermissionAxis.titleRes
    get() = when (this) {
        McpPermissionAxis.READ -> Res.string.mcp_permission_read_title
        McpPermissionAxis.RECORD -> Res.string.mcp_permission_record_title
        McpPermissionAxis.REMOVE -> Res.string.mcp_permission_remove_title
        McpPermissionAxis.OPERATE -> Res.string.mcp_permission_operate_title
    }

private val McpPermissionAxis.descriptionRes
    get() = when (this) {
        McpPermissionAxis.READ -> Res.string.mcp_permission_read_description
        McpPermissionAxis.RECORD -> Res.string.mcp_permission_record_description
        McpPermissionAxis.REMOVE -> Res.string.mcp_permission_remove_description
        McpPermissionAxis.OPERATE -> Res.string.mcp_permission_operate_description
    }

/** The glance at what an agent has been doing, with the whole log and its clearing one tap away. */
@Composable
private fun McpActivityCard(
    entries: List<McpActivityUi>,
    onOpenActivity: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) = SectionCard(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.mcp_activity_title),
            modifier = Modifier.weight(1f),
            style = typography.titleMedium,
            color = colorScheme.onSurface,
        )

        McpActivityClearButton(enabled = entries.isNotEmpty(), onClear = onClear)

        TextButton(onClick = onOpenActivity, modifier = Modifier.testTag("mcp_activity_see_all")) {
            Text(text = stringResource(Res.string.mcp_activity_see_all))
        }
    }

    if (entries.isEmpty()) {
        McpActivityEmpty()
        return@SectionCard
    }

    entries.forEachIndexed { index, entry ->
        if (index > 0) HorizontalDivider(color = colorScheme.outlineVariant)
        McpActivityRow(entry = entry)
    }
}
