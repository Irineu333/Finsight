@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backupHistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.extension.LocalPlatformContext
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_history_empty_message
import com.neoutils.finsight.resources.backup_history_empty_off
import com.neoutils.finsight.resources.backup_history_empty_title
import com.neoutils.finsight.resources.backup_history_failed
import com.neoutils.finsight.resources.backup_history_migration_label
import com.neoutils.finsight.resources.backup_history_migration_subtitle
import com.neoutils.finsight.resources.backup_history_summary
import com.neoutils.finsight.resources.backup_history_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.confirmRestore.ConfirmRestoreModal
import com.neoutils.finsight.ui.modal.restoreWithoutCopy.RestoreWithoutCopyModal
import com.neoutils.finsight.ui.modal.storedBackupActions.StoredBackupActionsModal
import com.neoutils.finsight.ui.screen.backup.copiesLabel
import com.neoutils.finsight.ui.screen.backup.destinationLabel
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.sizeLabel
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.UiText
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The copies the vault is keeping: what is in the destination, right now.
 *
 * It is a screen of its own and not a section of the backup screen (design D15). The list
 * grows with the retention in force — twenty items, or every one ever taken — each item has
 * actions, and this is the screen of the reunion: after reinstalling and pointing at the
 * folder again, what a person does here is choose between forty copies, not read settings.
 *
 * What is listed is what the file system answered when the screen opened. A copy deleted
 * with a file manager is simply not in it and no error is made of that — the history *is*
 * the folder (design D9).
 */
@Composable
fun BackupHistoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BackupHistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val analytics = koinInject<Analytics>()
    val modalManager = LocalModalManager.current
    val platformContext = LocalPlatformContext.current
    val dateFormats = LocalDateFormats.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("backup_history")
    }

    ConfirmRestoreHost(
        confirmation = uiState.confirmation,
        isRestoring = viewModel.isRestoring,
        keepsCopy = viewModel.keepsCopy,
        onAction = viewModel::onAction,
    )

    RestoreWithoutCopyHost(
        refusal = uiState.captureRefusal,
        onAction = viewModel::onAction,
    )

    Scaffold(
        modifier = Modifier.testTag("screen_backup_history"),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.backup_history_title)) },
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
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.copies.isEmpty() -> Empty(
                message = when {
                    uiState.isUnreadable -> stringResource(Res.string.backup_history_failed)
                    uiState.isVaultOn -> stringResource(Res.string.backup_history_empty_message)
                    else -> stringResource(Res.string.backup_history_empty_off)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "destination") {
                    DestinationHeader(
                        where = destinationLabel(uiState.destination),
                        summary = stringResource(
                            Res.string.backup_history_summary,
                            copiesLabel(uiState.copies.size),
                            sizeLabel(uiState.totalBytes),
                        ),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateItem(),
                    )
                }

                uiState.copies
                    .groupBy { dateFormats.toLocalDate(it.savedAt) }
                    .forEach { (date, copies) ->
                        item(key = "date_title_$date") {
                            Text(
                                text = dateFormats.formatRelativeDate(date),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .padding(horizontal = 16.dp)
                                    .animateItem(),
                            )
                        }

                        items(items = copies, key = { it.name }) { copy ->
                            StoredBackupCard(
                                backup = copy,
                                isWorking = uiState.working == copy,
                                enabled = !uiState.isBusy,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .animateItem(),
                                onClick = {
                                    modalManager.show(
                                        StoredBackupActionsModal(
                                            backup = copy,
                                            onRestore = {
                                                modalManager.dismiss()
                                                viewModel.onAction(
                                                    BackupHistoryAction.Restore(copy)
                                                )
                                            },
                                            onShare = {
                                                modalManager.dismiss()
                                                viewModel.onAction(
                                                    BackupHistoryAction.Share(copy, platformContext)
                                                )
                                            },
                                            onRemove = {
                                                modalManager.dismiss()
                                                viewModel.onAction(
                                                    BackupHistoryAction.Remove(copy)
                                                )
                                            },
                                        )
                                    )
                                },
                            )
                        }
                    }
            }
        }
    }
}

/**
 * Where the files are and what they add up to — the question that always comes with a list
 * of backups, answered once at the top instead of by counting rows.
 */
@Composable
private fun DestinationHeader(
    where: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_history_destination"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = where,
                style = typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One copy, said in the least that lets it be recognised without being opened: when it was
 * taken, how big it is, and — for the one taken before a migration — what it is for.
 *
 * That copy is labelled because it is the one somebody goes looking for when a figure
 * stopped adding up after an update, and it is the one retention never counts (design D10).
 * Nothing else about a copy is shown, because nothing else is known without reading the
 * file, and the file is read when it is reached for.
 */
@Composable
private fun StoredBackupCard(
    backup: StoredBackup,
    isWorking: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dateFormats = LocalDateFormats.current
    val isFromMigration = backup.name == PRE_MIGRATION_BACKUP_NAME

    Surface(
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTag("backup_copy"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = dateFormats.formatInstantTime(backup.savedAt),
                    style = typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = if (isFromMigration) {
                        sizeLabel(backup.sizeInBytes) + " · " +
                            stringResource(Res.string.backup_history_migration_subtitle)
                    } else {
                        sizeLabel(backup.sizeInBytes)
                    },
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorScheme.onSurfaceVariant,
                    strokeWidth = 2.dp,
                )
            } else if (isFromMigration) {
                Surface(
                    color = colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("backup_copy_migration_label"),
                ) {
                    Text(
                        text = stringResource(Res.string.backup_history_migration_label),
                        style = typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Nothing kept yet — which is not an error and is not a list of zero items. It says when the
 * first copy happens, or that nothing will be kept until the vault is turned on.
 */
@Composable
private fun Empty(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp)
                .testTag("backup_history_empty"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(Res.string.backup_history_empty_title),
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Puts the confirmation up while there is one, and takes it down when there is not — the
 * same arrangement the backup screen uses, because the sheet is rendered by the modal
 * manager outside this tree and the state is what keeps the two in step.
 */
@Composable
private fun ConfirmRestoreHost(
    confirmation: RestoreConfirmation?,
    isRestoring: StateFlow<Boolean>,
    keepsCopy: StateFlow<Boolean>,
    onAction: (BackupHistoryAction) -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (confirmation != null) {
        val modal = remember(confirmation) {
            ConfirmRestoreModal(
                confirmation = confirmation,
                isRestoring = isRestoring,
                keepsCopy = keepsCopy,
                onConfirm = { onAction(BackupHistoryAction.ConfirmRestore) },
                onDiscard = { onAction(BackupHistoryAction.DiscardCandidate) },
            )
        }

        DisposableEffect(modal) {
            modalManager.show(modal)
            onDispose { modalManager.dismiss(modal) }
        }
    }
}

@Composable
private fun RestoreWithoutCopyHost(
    refusal: UiText?,
    onAction: (BackupHistoryAction) -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (refusal != null) {
        val modal = remember(refusal) {
            RestoreWithoutCopyModal(
                reason = refusal,
                onProceed = { onAction(BackupHistoryAction.RestoreWithoutCopy) },
                onAbandon = { onAction(BackupHistoryAction.AbandonRestore) },
            )
        }

        DisposableEffect(modal) {
            modalManager.show(modal)
            onDispose { modalManager.dismiss(modal) }
        }
    }
}
