package com.neoutils.finsight.ui.screen.mcp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_activity_clear
import com.neoutils.finsight.resources.mcp_activity_empty_description
import com.neoutils.finsight.resources.mcp_activity_empty_title
import com.neoutils.finsight.resources.mcp_activity_outcome_refused
import com.neoutils.finsight.resources.mcp_activity_reference_gone
import com.neoutils.finsight.ui.component.EmptyStateMessage
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.clearAgentActivity.ClearAgentActivityModal
import com.neoutils.finsight.util.LocalDateFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * One act of an agent, and **the way from it to what it did**.
 *
 * The row is the only place in the app where authorship of a write appears: reactivity delivers the
 * result — the posting is simply there — and says nothing about it having come from outside. So the
 * row carries the whole of the record: when, which operation, what it was about in the words that
 * were true then, how it ended, and where to go and see it.
 *
 * A reference that no longer resolves does not invalidate the entry. What it created may well have
 * been removed since; the act still happened, so the row stays, says so, and stops offering a door
 * that leads nowhere.
 */
@Composable
internal fun McpActivityRow(entry: McpActivityUi) {
    val dateFormats = LocalDateFormats.current
    val navController = LocalNavController.current
    val detailController = LocalDetailPaneController.current
    val transactionsEntry = koinInject<TransactionsEntry>()

    val target = entry.target.takeUnless { entry.isTargetGone }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (target == null) {
                    Modifier
                } else {
                    Modifier.clickable {
                        when (target) {
                            is McpActivityTarget.Posting ->
                                detailController.show(transactionsEntry.viewTransactionModal(target.transactionId))

                            is McpActivityTarget.Section -> navController.navigate(target.route)
                        }
                    }
                }
            )
            .padding(vertical = 8.dp)
            .testTag("mcp_activity_item"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.operation,
                modifier = Modifier.weight(1f),
                style = typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = colorScheme.onSurface,
            )

            if (entry.isRefused) {
                Text(
                    text = stringResource(Res.string.mcp_activity_outcome_refused),
                    modifier = Modifier.testTag("mcp_activity_refused"),
                    style = typography.labelMedium,
                    color = colorScheme.error,
                )
            }
        }

        Text(
            text = entry.summary,
            style = typography.bodyMedium,
            color = colorScheme.onSurface,
        )

        if (entry.detail != null) {
            Text(
                text = entry.detail,
                style = typography.bodySmall,
                color = colorScheme.error,
            )
        }

        Text(
            text = "${dateFormats.formatInstantDate(entry.at)} · ${dateFormats.formatInstantTime(entry.at)}",
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )

        if (entry.isTargetGone) {
            Text(
                text = stringResource(Res.string.mcp_activity_reference_gone),
                modifier = Modifier.testTag("mcp_activity_reference_gone"),
                style = typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Clearing, behind the confirmation the one irreversible action in this section deserves. */
@Composable
internal fun McpActivityClearButton(
    enabled: Boolean,
    onClear: () -> Unit,
) {
    val modalManager = LocalModalManager.current

    TextButton(
        onClick = { modalManager.show(ClearAgentActivityModal(onConfirm = onClear)) },
        enabled = enabled,
        modifier = Modifier.testTag("mcp_activity_clear"),
    ) {
        Text(text = stringResource(Res.string.mcp_activity_clear))
    }
}

@Composable
internal fun McpActivityEmpty(modifier: Modifier = Modifier) {
    EmptyStateMessage(
        icon = Icons.Default.History,
        title = stringResource(Res.string.mcp_activity_empty_title),
        description = stringResource(Res.string.mcp_activity_empty_description),
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}
