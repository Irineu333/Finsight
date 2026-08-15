@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_activity_client_declared
import com.neoutils.finsight.resources.mcp_activity_client_unknown
import com.neoutils.finsight.resources.mcp_activity_empty
import com.neoutils.finsight.resources.mcp_activity_open_entity
import com.neoutils.finsight.resources.mcp_activity_outcome_failed
import com.neoutils.finsight.resources.mcp_activity_outcome_ok
import com.neoutils.finsight.resources.mcp_activity_outcome_refused
import com.neoutils.finsight.resources.mcp_activity_title
import com.neoutils.finsight.resources.mcp_address_label
import com.neoutils.finsight.resources.mcp_client_config_copy
import com.neoutils.finsight.resources.mcp_client_config_description
import com.neoutils.finsight.resources.mcp_client_config_local_only
import com.neoutils.finsight.resources.mcp_client_config_protocol_revision
import com.neoutils.finsight.resources.mcp_client_config_read_only_notice
import com.neoutils.finsight.resources.mcp_client_config_title
import com.neoutils.finsight.resources.mcp_enabled_description
import com.neoutils.finsight.resources.mcp_enabled_label
import com.neoutils.finsight.resources.mcp_off_notice
import com.neoutils.finsight.resources.mcp_permission_read_only
import com.neoutils.finsight.resources.mcp_permission_read_only_description
import com.neoutils.finsight.resources.mcp_permission_read_write
import com.neoutils.finsight.resources.mcp_permission_read_write_description
import com.neoutils.finsight.resources.mcp_permission_title
import com.neoutils.finsight.resources.mcp_port_apply
import com.neoutils.finsight.resources.mcp_port_conflict_unknown_process
import com.neoutils.finsight.resources.mcp_port_label
import com.neoutils.finsight.resources.mcp_screen_title
import com.neoutils.finsight.resources.mcp_status_off
import com.neoutils.finsight.resources.mcp_status_port_in_use
import com.neoutils.finsight.resources.mcp_status_port_in_use_detail
import com.neoutils.finsight.resources.mcp_status_running
import com.neoutils.finsight.resources.mcp_token_hidden
import com.neoutils.finsight.resources.mcp_token_hide
import com.neoutils.finsight.resources.mcp_token_label
import com.neoutils.finsight.resources.mcp_token_rotate
import com.neoutils.finsight.resources.mcp_token_rotate_warning
import com.neoutils.finsight.resources.mcp_token_show
import com.neoutils.finsight.ui.component.LocalModalManager
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The MCP server: the one place the capability is switched on, and the one screen that says what
 * is actually happening.
 *
 * **It renders three states and decides none of them.** Which state the screen is in comes from
 * [McpUiState], and so does everything that follows from it — whether a connection snippet exists
 * at all, whether the read-only warning belongs in the instructions, where a journal line leads.
 * A composable that decided any of that would be a second owner of the answer.
 *
 * Off offers no snippet on purpose: with nothing listening, something copyable produces a client
 * that fails and a user who blames the client. On offers everything at once — address, level,
 * token, instructions, activity — because a switch turned on with a token nobody pasted anywhere
 * is a state that looks like it works and does not.
 */
@Composable
fun McpScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: McpViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        analytics.logScreenView("mcp_server")
    }

    Scaffold(
        modifier = Modifier.testTag("screen_mcp"),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val state = uiState) {
                is McpUiState.Off -> OffState(state, viewModel::onAction)
                is McpUiState.Listening -> ListeningState(state, viewModel::onAction)
                is McpUiState.PortUnavailable -> PortUnavailableState(state, viewModel::onAction)
            }
        }
    }
}

/**
 * Nothing is listening: the switch, what turning it on means, and nothing else.
 *
 * No address, no level, no token and no snippet — every one of them would describe a server that
 * does not exist.
 */
@Composable
private fun OffState(state: McpUiState.Off, onAction: (McpAction) -> Unit) {
    Section {
        EnabledSwitch(isEnabled = state.isEnabled, onAction = onAction)
        Text(
            text = stringResource(Res.string.mcp_enabled_description),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.mcp_off_notice),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("mcp_off_notice"),
        )
        StatusLine(text = stringResource(Res.string.mcp_status_off), tag = "mcp_status_off")
    }
}

/** Listening: everything a user needs to connect, at once, at the very first `on`. */
@Composable
private fun ListeningState(state: McpUiState.Listening, onAction: (McpAction) -> Unit) {
    Section {
        EnabledSwitch(isEnabled = true, onAction = onAction)
        StatusLine(text = stringResource(Res.string.mcp_status_running), tag = "mcp_status_running")
        LabelledValue(
            label = stringResource(Res.string.mcp_address_label),
            value = state.endpoint,
            tag = "mcp_address",
        )
    }

    PermissionSection(permission = state.permission, onAction = onAction)

    TokenSection(state = state, onAction = onAction)

    ClientConfigSection(state = state)

    ActivitySection(activity = state.activity)
}

/**
 * The server did not start because the port is taken.
 *
 * Neither on nor off: it says nothing is listening, names the conflict, and offers choosing
 * another port. The switch is not shown here as a position, because a switch is exactly what
 * would make this read as one of the other two states.
 */
@Composable
private fun PortUnavailableState(state: McpUiState.PortUnavailable, onAction: (McpAction) -> Unit) {
    Section {
        StatusLine(
            text = stringResource(Res.string.mcp_status_port_in_use, state.port),
            tag = "mcp_status_port_in_use",
        )
        Text(
            text = stringResource(
                Res.string.mcp_status_port_in_use_detail,
                state.port,
                stringResource(Res.string.mcp_port_conflict_unknown_process),
            ),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        // What the operating system said, verbatim. It is not prose for the user, and it is not
        // dressed up as such — it is what makes the conflict diagnosable when the sentence above
        // is not enough.
        Text(
            text = state.reason,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        PortPicker(port = state.port, onAction = onAction)
    }
}

/** The single switch of the capability — it exists on this screen and nowhere else in the app. */
@Composable
private fun EnabledSwitch(isEnabled: Boolean, onAction: (McpAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.mcp_enabled_label),
            style = typography.titleMedium,
        )
        Switch(
            modifier = Modifier.testTag("mcp_enabled_switch"),
            checked = isEnabled,
            onCheckedChange = { onAction(McpAction.SetEnabled(it)) },
        )
    }
}

/** Both levels, always, with the one in force marked — a choice is not a choice half shown. */
@Composable
private fun PermissionSection(permission: McpPermission, onAction: (McpAction) -> Unit) {
    Section {
        Text(
            text = stringResource(Res.string.mcp_permission_title),
            style = typography.titleMedium,
        )
        PermissionOption(
            title = stringResource(Res.string.mcp_permission_read_only),
            description = stringResource(Res.string.mcp_permission_read_only_description),
            isSelected = permission == McpPermission.READ_ONLY,
            tag = "mcp_permission_read_only",
            onSelect = { onAction(McpAction.SetPermission(McpPermission.READ_ONLY)) },
        )
        PermissionOption(
            title = stringResource(Res.string.mcp_permission_read_write),
            description = stringResource(Res.string.mcp_permission_read_write_description),
            isSelected = permission == McpPermission.READ_WRITE,
            tag = "mcp_permission_read_write",
            onSelect = { onAction(McpAction.SetPermission(McpPermission.READ_WRITE)) },
        )
    }
}

@Composable
private fun PermissionOption(
    title: String,
    description: String,
    isSelected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onSelect)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = onSelect)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = typography.bodyLarge)
            Text(
                text = description,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The token: hidden by default, revealed deliberately, rotated deliberately. */
@Composable
private fun TokenSection(state: McpUiState.Listening, onAction: (McpAction) -> Unit) {
    Section {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.mcp_token_label), style = typography.titleMedium)
                Text(
                    modifier = Modifier.testTag("mcp_token_value"),
                    text = if (state.isTokenVisible) {
                        state.token
                    } else {
                        stringResource(Res.string.mcp_token_hidden)
                    },
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                modifier = Modifier.testTag("mcp_token_visibility"),
                onClick = { onAction(McpAction.ToggleTokenVisibility) },
            ) {
                Text(
                    text = if (state.isTokenVisible) {
                        stringResource(Res.string.mcp_token_hide)
                    } else {
                        stringResource(Res.string.mcp_token_show)
                    },
                )
            }
        }
        OutlinedButton(
            modifier = Modifier.testTag("mcp_token_rotate"),
            onClick = { onAction(McpAction.RotateToken) },
        ) {
            Text(text = stringResource(Res.string.mcp_token_rotate))
        }
        Text(
            text = stringResource(Res.string.mcp_token_rotate_warning),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The instructions: the snippet ready to paste, and the three things the user cannot deduce —
 * that the access is local, that read-only means no write will be visible (and where to change
 * it), and which revision of the protocol the server speaks.
 */
@Composable
private fun ClientConfigSection(state: McpUiState.Listening) {
    val clipboard = LocalClipboardManager.current

    Section {
        Text(
            text = stringResource(Res.string.mcp_client_config_title),
            style = typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.mcp_client_config_description),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHighest),
        ) {
            Text(
                modifier = Modifier
                    .padding(12.dp)
                    .testTag("mcp_client_config_snippet"),
                text = state.clientConfig,
                style = typography.bodySmall,
            )
        }
        OutlinedButton(
            modifier = Modifier.testTag("mcp_client_config_copy"),
            onClick = { clipboard.setText(AnnotatedString(state.clientConfig)) },
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = stringResource(Res.string.mcp_client_config_copy),
            )
        }
        Text(
            text = stringResource(Res.string.mcp_client_config_local_only),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        if (state.isReadOnly) {
            Text(
                modifier = Modifier.testTag("mcp_client_config_read_only_notice"),
                text = stringResource(Res.string.mcp_client_config_read_only_notice),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                Res.string.mcp_client_config_protocol_revision,
                state.protocolRevision,
            ),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The journal, reactive.
 *
 * **There is no undo here.** A line leads to the entity it touched — the same modal the rest of
 * the app opens a transaction with — and the inverse operation, when the domain offers one, lives
 * there. A command promising to revert any line would promise what not every operation has.
 */
@Composable
private fun ActivitySection(activity: List<AgentActivityUi>) {
    val modalManager = LocalModalManager.current
    val transactions = koinInject<TransactionsEntry>()

    Section {
        Text(
            text = stringResource(Res.string.mcp_activity_title),
            style = typography.titleMedium,
        )

        if (activity.isEmpty()) {
            Text(
                modifier = Modifier.testTag("mcp_activity_empty"),
                text = stringResource(Res.string.mcp_activity_empty),
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            return@Section
        }

        activity.forEachIndexed { index, record ->
            if (index > 0) HorizontalDivider()
            ActivityRow(
                record = record,
                onOpen = { target ->
                    when (target) {
                        is AgentActivityTarget.Transaction -> modalManager.show(
                            transactions.viewTransactionModal(target.id),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ActivityRow(record: AgentActivityUi, onOpen: (AgentActivityTarget) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mcp_activity_item"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = record.tool, style = typography.bodyLarge)
            Text(
                text = "${record.timestamp} · ${outcomeLabel(record.outcome)}",
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            // Whatever the client called itself, said as a claim and not as a fact: what
            // authenticates is the token, and it is the same for every client.
            Text(
                text = record.client
                    ?.let { stringResource(Res.string.mcp_activity_client_declared, it) }
                    ?: stringResource(Res.string.mcp_activity_client_unknown),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        record.target?.let { target ->
            IconButton(
                modifier = Modifier.testTag("mcp_activity_open"),
                onClick = { onOpen(target) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.mcp_activity_open_entity),
                )
            }
        }
    }
}

@Composable
private fun outcomeLabel(outcome: AgentActivityOutcome): String = when (outcome) {
    AgentActivityOutcome.OK -> stringResource(Res.string.mcp_activity_outcome_ok)
    AgentActivityOutcome.REFUSED -> stringResource(Res.string.mcp_activity_outcome_refused)
    AgentActivityOutcome.FAILED -> stringResource(Res.string.mcp_activity_outcome_failed)
}

/** Choosing another port, deliberately — the only way out of a conflict, since none is assumed. */
@Composable
private fun PortPicker(port: Int, onAction: (McpAction) -> Unit) {
    var typed by remember(port) { mutableStateOf(port.toString()) }
    val chosen = typed.toIntOrNull()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .weight(1f)
                .testTag("mcp_port_field"),
            value = typed,
            onValueChange = { typed = it.filter(Char::isDigit).take(5) },
            singleLine = true,
            label = { Text(text = stringResource(Res.string.mcp_port_label)) },
        )
        OutlinedButton(
            modifier = Modifier.testTag("mcp_port_apply"),
            enabled = chosen != null && chosen != port,
            onClick = { chosen?.let { onAction(McpAction.SetPort(it)) } },
        ) {
            Text(text = stringResource(Res.string.mcp_port_apply))
        }
    }
}

@Composable
private fun StatusLine(text: String, tag: String) {
    Text(
        modifier = Modifier.testTag(tag),
        text = text,
        style = typography.labelLarge,
        color = colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LabelledValue(label: String, value: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = typography.labelLarge, color = colorScheme.onSurfaceVariant)
        Text(modifier = Modifier.testTag(tag), text = value, style = typography.bodyLarge)
    }
}

/** One card, one subject — the anatomy every block of this screen wears. */
@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() },
        )
    }
}
