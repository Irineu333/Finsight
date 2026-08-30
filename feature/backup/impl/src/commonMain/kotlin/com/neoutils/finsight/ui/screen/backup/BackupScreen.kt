@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.extension.LocalPlatformContext
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_coverage_app
import com.neoutils.finsight.resources.backup_coverage_folder
import com.neoutils.finsight.resources.backup_export_subtitle
import com.neoutils.finsight.resources.backup_export_title
import com.neoutils.finsight.resources.backup_group_automatic
import com.neoutils.finsight.resources.backup_group_manual
import com.neoutils.finsight.resources.backup_history_subtitle
import com.neoutils.finsight.resources.backup_history_subtitle_empty
import com.neoutils.finsight.resources.backup_history_title
import com.neoutils.finsight.resources.backup_last_never
import com.neoutils.finsight.resources.backup_last_never_hint
import com.neoutils.finsight.resources.backup_last_overdue
import com.neoutils.finsight.resources.backup_last_title
import com.neoutils.finsight.resources.backup_no_copies
import com.neoutils.finsight.resources.backup_restore_subtitle
import com.neoutils.finsight.resources.backup_restore_subtitle_vault
import com.neoutils.finsight.resources.backup_restore_title
import com.neoutils.finsight.resources.backup_scope
import com.neoutils.finsight.resources.backup_screen_title
import com.neoutils.finsight.resources.backup_settings_subtitle
import com.neoutils.finsight.resources.backup_settings_title
import com.neoutils.finsight.resources.backup_today
import com.neoutils.finsight.resources.backup_vault_off_subtitle
import com.neoutils.finsight.resources.backup_vault_on_both
import com.neoutils.finsight.resources.backup_vault_on_periodic
import com.neoutils.finsight.resources.backup_vault_on_preventive
import com.neoutils.finsight.resources.backup_vault_on_update_only
import com.neoutils.finsight.resources.backup_vault_title
import com.neoutils.finsight.resources.backup_yesterday
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.confirmRestore.ConfirmRestoreModal
import com.neoutils.finsight.ui.modal.restoreWithoutCopy.RestoreWithoutCopyModal
import com.neoutils.finsight.ui.modal.vaultSettings.VaultSettingsModal
import com.neoutils.finsight.ui.theme.SettingsTileTheme
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.theme.finsightSwitchColors
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Backup: what the app keeps on its own, and the file the user takes out of it or brings
 * back into it.
 *
 * **The most important thing on it is not the switch.** A vault can stop working without a
 * defect and without anybody doing anything — an access revoked, a folder moved, the system
 * suspending the app — and the instant of the last successful copy is the only way a person
 * finds that out (design D12). An app that says "on" while it has written nothing for seven
 * months is worse than an app with no vault at all, so that line is at the top and it
 * carries the sign of a copy that has aged past the interval.
 *
 * **Two groups, not four.** What the app does on its own, and what leaves and enters by the
 * user's hand. The two headings this replaced sat over one tile each and repeated the
 * tile's own name, which grouped nothing.
 *
 * **The card says what the file holds and what the destination does not cover.** With the
 * vault off, it also says the app keeps no copies of its own — a promise that stops being
 * true the moment somebody turns the vault on, which is why it is stated as a consequence
 * of the switch rather than as a property of the app.
 */
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: BackupViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val platformContext = LocalPlatformContext.current
    val analytics = koinInject<Analytics>()
    val modalManager = LocalModalManager.current
    val clock = koinInject<Clock>()

    LaunchedEffect(Unit) {
        analytics.logScreenView("backup")
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
        modifier = Modifier.testTag("screen_backup"),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.backup_screen_title)) },
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
        SettingsTileTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (uiState.vault.isOn) {
                    LastBackupCard(
                        vault = uiState.vault,
                        copies = uiState.copies,
                        now = clock.now(),
                    )
                }

                ScopeCard(vault = uiState.vault)

                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.backup_group_automatic)) },
                ) {
                    SettingsMenuLink(
                        modifier = Modifier.testTag("backup_vault"),
                        shape = TileShape,
                        icon = {
                            Icon(imageVector = Icons.Outlined.Shield, contentDescription = null)
                        },
                        title = { Text(text = stringResource(Res.string.backup_vault_title)) },
                        subtitle = { Text(text = vaultSubtitle(uiState.vault)) },
                        action = {
                            Switch(
                                checked = uiState.vault.isOn,
                                onCheckedChange = { viewModel.onAction(BackupAction.SetVaultOn(it)) },
                                colors = finsightSwitchColors(),
                                modifier = Modifier.testTag("backup_vault_switch"),
                            )
                        },
                        onClick = {
                            viewModel.onAction(BackupAction.SetVaultOn(!uiState.vault.isOn))
                        },
                    )

                    if (uiState.vault.isOn) {
                        SettingsMenuLink(
                            modifier = Modifier.testTag("backup_vault_settings_tile"),
                            shape = TileShape,
                            icon = {
                                Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
                            },
                            title = { Text(text = stringResource(Res.string.backup_settings_title)) },
                            subtitle = {
                                Text(text = stringResource(Res.string.backup_settings_subtitle))
                            },
                            action = { TileAction(isRunning = false) },
                            onClick = {
                                modalManager.show(
                                    VaultSettingsModal(
                                        state = viewModel.vaultState,
                                        copies = viewModel.storedCopies,
                                        onAction = viewModel::onAction,
                                    )
                                )
                            },
                        )

                        SettingsMenuLink(
                            modifier = Modifier.testTag("backup_history_tile"),
                            shape = TileShape,
                            icon = {
                                Icon(imageVector = Icons.Outlined.History, contentDescription = null)
                            },
                            title = { Text(text = stringResource(Res.string.backup_history_title)) },
                            subtitle = { Text(text = historySubtitle(uiState.copies)) },
                            action = { TileAction(isRunning = false) },
                            onClick = onNavigateToHistory,
                        )
                    }
                }

                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.backup_group_manual)) },
                ) {
                    SettingsMenuLink(
                        modifier = Modifier.testTag("backup_export"),
                        enabled = !uiState.isBusy,
                        shape = TileShape,
                        icon = { Icon(imageVector = Icons.Outlined.SaveAlt, contentDescription = null) },
                        title = { Text(text = stringResource(Res.string.backup_export_title)) },
                        subtitle = { Text(text = stringResource(Res.string.backup_export_subtitle)) },
                        action = { TileAction(isRunning = uiState.isExporting) },
                        onClick = { viewModel.onAction(BackupAction.Export(platformContext)) },
                    )

                    SettingsMenuLink(
                        modifier = Modifier.testTag("backup_restore"),
                        enabled = !uiState.isBusy,
                        shape = TileShape,
                        icon = { Icon(imageVector = Icons.Outlined.Restore, contentDescription = null) },
                        title = { Text(text = stringResource(Res.string.backup_restore_title)) },
                        subtitle = {
                            // With copies of its own to restore from, picking a file is the
                            // specific case rather than the only one.
                            Text(
                                text = if (uiState.vault.isOn) {
                                    stringResource(Res.string.backup_restore_subtitle_vault)
                                } else {
                                    stringResource(Res.string.backup_restore_subtitle)
                                }
                            )
                        },
                        action = { TileAction(isRunning = uiState.isVerifying) },
                        onClick = {
                            viewModel.onAction(BackupAction.ChooseFileToRestore(platformContext))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Puts the confirmation up while there is one, and takes it down when there is not.
 *
 * The sheet is rendered by the modal manager, outside this tree, so what keeps the two in
 * step is the state: the view model publishes an approved file and this shows it; the view
 * model drops it — because the replacement finished, because it failed, or because the
 * sheet was dismissed — and this dismisses whatever is still standing. Dismissing a modal
 * the manager no longer holds does nothing, which is what makes the last case harmless.
 */
@Composable
private fun ConfirmRestoreHost(
    confirmation: RestoreConfirmation?,
    isRestoring: StateFlow<Boolean>,
    keepsCopy: StateFlow<Boolean>,
    onAction: (BackupAction) -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (confirmation != null) {
        val modal = remember(confirmation) {
            ConfirmRestoreModal(
                confirmation = confirmation,
                isRestoring = isRestoring,
                keepsCopy = keepsCopy,
                onConfirm = { onAction(BackupAction.Restore) },
                onDiscard = { onAction(BackupAction.DiscardCandidate) },
            )
        }

        DisposableEffect(modal) {
            modalManager.show(modal)
            onDispose { modalManager.dismiss(modal) }
        }
    }
}

/**
 * Puts the question about restoring without a copy up while there is one, over the
 * confirmation that is still standing.
 *
 * The same arrangement as [ConfirmRestoreHost], and for the same reason: the sheet is
 * rendered by the modal manager outside this tree, so the state is what keeps the two in
 * step. The view model publishes a refusal and this shows it; the view model drops it —
 * because an answer came, or because the flow ended — and this takes down whatever is left.
 */
@Composable
private fun RestoreWithoutCopyHost(
    refusal: UiText?,
    onAction: (BackupAction) -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (refusal != null) {
        val modal = remember(refusal) {
            RestoreWithoutCopyModal(
                reason = refusal,
                onProceed = { onAction(BackupAction.RestoreWithoutCopy) },
                onAbandon = { onAction(BackupAction.AbandonRestore) },
            )
        }

        DisposableEffect(modal) {
            modalManager.show(modal)
            onDispose { modalManager.dismiss(modal) }
        }
    }
}

/**
 * When the last copy that actually landed was taken, where it went, and whether it has
 * aged past the interval.
 *
 * Never captured is written out as such and never as a date: "never" and "a long time ago"
 * lead to different actions, and the spec forbids showing a date that stands for neither.
 *
 * The sign of a copy that has aged is amber rather than red, because nothing is broken —
 * the copies that exist are still there and still valid. Red is what a vault that can no
 * longer write would deserve.
 */
@Composable
private fun LastBackupCard(vault: VaultState, copies: VaultCopies, now: Instant) {
    val dateFormats = LocalDateFormats.current
    val last = vault.lastCapturedAt
    val overdue = last != null && now - last >= vault.interval
    val tone = when {
        last == null -> colorScheme.onSurfaceVariant
        overdue -> Warning
        else -> colorScheme.primary
    }

    Surface(
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_last_capture"),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(tone),
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.backup_last_title),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = last?.let {
                        dateFormats.formatDividerDate(
                            instant = it,
                            today = stringResource(Res.string.backup_today),
                            yesterday = stringResource(Res.string.backup_yesterday),
                        ) + ", " + dateFormats.formatInstantTime(it)
                    } ?: stringResource(Res.string.backup_last_never),
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tone,
                )
                Text(
                    text = when {
                        last == null -> stringResource(Res.string.backup_last_never_hint)
                        overdue -> stringResource(
                            Res.string.backup_last_overdue,
                            intervalLabel(VaultInterval.nearest(vault.interval)),
                        )

                        else -> destinationLabel(vault.destination) +
                            " · " + copiesLabel(copies.count)
                    },
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What the file carries, and what the destination in force leaves uncovered.
 *
 * The second sentence is the switch's consequence: off, the app keeps nothing of its own
 * and moving to another device recovers only what was exported; on, the copies exist and
 * the sentence names what the place they are in does not protect against (design D3).
 */
@Composable
private fun ScopeCard(vault: VaultState) {
    Surface(
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_coverage"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.backup_scope),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (vault.isOn) {
                        coverageOf(vault.destination)
                    } else {
                        stringResource(Res.string.backup_no_copies)
                    },
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What each rung of protection does not cover, in its own words (design D3). */
@Composable
private fun coverageOf(destination: VaultDestination): String = when (destination) {
    VaultDestination.APP_STORAGE -> stringResource(Res.string.backup_coverage_app)
    VaultDestination.USER_FOLDER -> stringResource(Res.string.backup_coverage_folder)
}

/** Which occasions produce a copy, said as occasions rather than as switches. */
@Composable
private fun vaultSubtitle(vault: VaultState): String {
    if (!vault.isOn) return stringResource(Res.string.backup_vault_off_subtitle)

    val interval = intervalLabel(VaultInterval.nearest(vault.interval))

    return when {
        vault.isPeriodicOn && vault.isPreventiveOn ->
            stringResource(Res.string.backup_vault_on_both, interval)

        vault.isPeriodicOn -> stringResource(Res.string.backup_vault_on_periodic, interval)
        vault.isPreventiveOn -> stringResource(Res.string.backup_vault_on_preventive)
        // Both switched off leaves the copy taken before a migration, which has no switch
        // of its own and rides on the vault (design D1). Saying "nothing" here would be a
        // lie about the one occasion that is left.
        else -> stringResource(Res.string.backup_vault_on_update_only)
    }
}

/** How many copies are kept, and when the newest of them landed. */
@Composable
private fun historySubtitle(copies: VaultCopies): String {
    val newest = copies.newestAt ?: return stringResource(Res.string.backup_history_subtitle_empty)
    val dateFormats = LocalDateFormats.current

    return stringResource(
        Res.string.backup_history_subtitle,
        copiesLabel(copies.count),
        dateFormats.formatDividerDate(
            instant = newest,
            today = stringResource(Res.string.backup_today),
            yesterday = stringResource(Res.string.backup_yesterday),
        ) + ", " + dateFormats.formatInstantTime(newest),
    )
}

/** The chevron, or the wait it turns into while the entry's own operation runs. */
@Composable
private fun TileAction(isRunning: Boolean) {
    if (isRunning) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp,
        )
    } else {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

/** The corner every card of this app wears. */
private val TileShape = RoundedCornerShape(12.dp)
