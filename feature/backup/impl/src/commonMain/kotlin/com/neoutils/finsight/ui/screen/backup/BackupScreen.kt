@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultRung
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.extension.LocalPlatformContext
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_folder_keep_in_app
import com.neoutils.finsight.resources.backup_folder_reconnect
import com.neoutils.finsight.resources.backup_folder_unreachable
import com.neoutils.finsight.resources.backup_export_subtitle
import com.neoutils.finsight.resources.backup_export_title
import com.neoutils.finsight.resources.backup_group_automatic
import com.neoutils.finsight.resources.backup_group_manual
import com.neoutils.finsight.resources.backup_history_title
import com.neoutils.finsight.resources.backup_last_never
import com.neoutils.finsight.resources.backup_last_never_hint
import com.neoutils.finsight.resources.backup_last_never_hint_periodic
import com.neoutils.finsight.resources.backup_last_never_hint_preventive
import com.neoutils.finsight.resources.backup_last_never_hint_update_only
import com.neoutils.finsight.resources.backup_last_overdue
import com.neoutils.finsight.resources.backup_last_title
import com.neoutils.finsight.resources.backup_no_copies
import com.neoutils.finsight.resources.backup_restore_subtitle
import com.neoutils.finsight.resources.backup_restore_subtitle_vault
import com.neoutils.finsight.resources.backup_restore_title
import com.neoutils.finsight.resources.backup_screen_title
import com.neoutils.finsight.resources.backup_settings_subtitle
import com.neoutils.finsight.resources.backup_settings_title
import com.neoutils.finsight.resources.backup_today
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
 * **It is a list, and it is built as one.** Turning the vault on adds three rows at once —
 * the status card and the two tiles that only mean something with copies to point at — and
 * a `Column` had them appear from nothing while everything below jumped. Every row is a
 * keyed item, so the list fades in what arrives and slides what was already there.
 *
 * **Nothing on it explains the feature at rest.** What the destination does not cover rides
 * with the status card that names the destination, and the consequence of leaving the vault
 * off is the vault tile's own subtitle. What a backup file holds is said where a file is
 * about to replace the archive, in the restore confirmation.
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

    // What the destination holds is read from a folder, not observed from a table, so
    // nothing tells this screen that the copies screen on top of it deleted one. Coming
    // back is the occasion, and the entry's own lifecycle is what says so.
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(BackupAction.Refresh)
        onPauseOrDispose {}
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                backupRows {
                    if (uiState.vault.isOn) {
                        row(key = "last_capture") { modifier ->
                            LastBackupCard(
                                vault = uiState.vault,
                                copies = uiState.copiesInForce,
                                last = uiState.lastCopyAt,
                                rung = uiState.rung,
                                now = clock.now(),
                                onAction = viewModel::onAction,
                                platformContext = platformContext,
                                modifier = modifier,
                            )
                        }
                    }

                    row(key = "group_automatic", opensGroup = true) { modifier ->
                        GroupTitle(
                            text = stringResource(Res.string.backup_group_automatic),
                            modifier = modifier,
                        )
                    }

                    // The one tile that is a switch says so: the whole row toggles, under
                    // one id and announced once. A tile that carried its own switch would
                    // put a second target inside the first.
                    row(key = "vault") { modifier ->
                        SettingsSwitch(
                            modifier = modifier.testTag("backup_vault"),
                            shape = TileShape,
                            icon = {
                                Icon(imageVector = Icons.Outlined.Shield, contentDescription = null)
                            },
                            title = { Text(text = stringResource(Res.string.backup_vault_title)) },
                            subtitle = { Text(text = vaultSubtitle(uiState.vault)) },
                            state = uiState.vault.isOn,
                            // The tile's accent is spent on the glyph, so a switch left to
                            // take its colours from the tile would have no accent at all.
                            switchColors = finsightSwitchColors(),
                            onCheckedChange = { viewModel.onAction(BackupAction.SetVaultOn(it)) },
                        )
                    }

                    if (uiState.vault.isOn) {
                        row(key = "vault_settings") { modifier ->
                            SettingsMenuLink(
                                modifier = modifier.testTag("backup_vault_settings_tile"),
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
                        }

                        row(key = "history") { modifier ->
                            SettingsMenuLink(
                                modifier = modifier.testTag("backup_history_tile"),
                                shape = TileShape,
                                icon = {
                                    Icon(imageVector = Icons.Outlined.History, contentDescription = null)
                                },
                                title = { Text(text = stringResource(Res.string.backup_history_title)) },
                                // Where they are, which is also what is behind this door
                                // now that choosing the destination happens on the screen it
                                // opens. It needs no listing — the rung in force is known
                                // before the folder answers — so the tile never opens with a
                                // blank line under its name, and it says nothing about how
                                // many copies there are, which the card above already counts.
                                subtitle = { Text(text = destinationLabel(uiState.rung.inForce)) },
                                action = { TileAction(isRunning = false) },
                                onClick = onNavigateToHistory,
                            )
                        }
                    }

                    row(key = "group_manual", opensGroup = true) { modifier ->
                        GroupTitle(
                            text = stringResource(Res.string.backup_group_manual),
                            modifier = modifier,
                        )
                    }

                    row(key = "export") { modifier ->
                        SettingsMenuLink(
                            modifier = modifier.testTag("backup_export"),
                            enabled = !uiState.isBusy,
                            shape = TileShape,
                            icon = { Icon(imageVector = Icons.Outlined.SaveAlt, contentDescription = null) },
                            title = { Text(text = stringResource(Res.string.backup_export_title)) },
                            subtitle = { Text(text = stringResource(Res.string.backup_export_subtitle)) },
                            action = { TileAction(isRunning = uiState.isExporting) },
                            onClick = { viewModel.onAction(BackupAction.Export(platformContext)) },
                        )
                    }

                    row(key = "restore") { modifier ->
                        SettingsMenuLink(
                            modifier = modifier.testTag("backup_restore"),
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
 * A group heading, standing as a row of its own.
 *
 * The tiles it labels are items of the list beside it rather than children of a group,
 * because a tile that arrives has to arrive on its own; what is left of the group is the
 * heading, and the tile library still owns how a heading looks. Order is what keeps the
 * two together — nothing is ever inserted between a heading and the tile under it.
 */
@Composable
private fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    SettingsGroup(
        modifier = modifier,
        title = { Text(text = text) },
        content = {},
    )
}

/**
 * When the last copy that actually landed was taken, where it went, whether it has aged
 * past the interval, and what the place it went to does not protect against.
 *
 * Never captured is written out as such and never as a date: "never" and "a long time ago"
 * lead to different actions, and the spec forbids showing a date that stands for neither.
 *
 * The sign of a copy that has aged is amber rather than red, because nothing is broken —
 * the copies that exist are still there and still valid. Red is what a vault that can no
 * longer write would deserve.
 *
 * **Whether it has aged is the vault's own reading and not a second one taken here**
 * ([VaultState.isLastCopyOverdue]). The wait belongs to the periodic trigger, so with that
 * trigger off nothing was going to capture on a schedule and the sentence the amber carries
 * — open the app more often and a new copy is taken — is one the app would not keep. The
 * instant itself is shown in every case, which is the whole of what design D12 asks for.
 *
 * The coverage sentence is here and not in a card of its own because it is the consequence
 * of the destination named two lines above it (`automatic-backup` spec), and it is only
 * ever true while there is a vault to have a destination.
 *
 * **A folder that cannot be reached is announced here, with both ways out beside it**
 * (design D12). The destination this names is then the one the copies are actually landing
 * in — inside the app, provisionally — because a card saying *in a folder you chose* over
 * copies going somewhere else is the failure this card exists to catch. Neither way out is
 * taken on anybody's behalf, and the capturing does not stop while the question stands.
 */
@Composable
private fun LastBackupCard(
    vault: VaultState,
    copies: VaultCopies,
    /**
     * The copy this names, from [BackupUiState.lastCopyAt] — the newest one standing where
     * the copies go, never an instant belonging to the rung left behind.
     */
    last: Instant?,
    rung: VaultRung,
    now: Instant,
    onAction: (BackupAction) -> Unit,
    platformContext: PlatformContext,
    modifier: Modifier = Modifier,
) {
    val dateFormats = LocalDateFormats.current
    val overdue = vault.isCopyOverdue(newest = last, now = now)
    val tone = when {
        // Red is what a folder that cannot be reached deserves: the copies already in it
        // are out of the app's reach, and the new ones are landing somewhere the person did
        // not choose (design D12).
        rung.isProvisional -> colorScheme.error
        last == null -> colorScheme.onSurfaceVariant
        overdue -> Warning
        else -> colorScheme.primary
    }

    Surface(
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
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
                        last == null -> neverHint(vault)
                        overdue -> stringResource(
                            Res.string.backup_last_overdue,
                            intervalLabel(VaultInterval.nearest(vault.interval)),
                        )

                        // The destination named is the one the copies are going to, which
                        // is not the one that was chosen while the folder behind it cannot
                        // be reached. The count joins it once that destination has answered,
                        // and not before: an unread one has no number to state.
                        else -> destinationLabel(rung.inForce) +
                            if (copies.isRead) " · " + copiesLabel(copies.count) else ""
                    },
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = coverageOf(rung.inForce),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("backup_coverage"),
                )

                // The loss, and the two ways out of it, in the place the loss is
                // announced (design D12). Neither is taken on anybody's behalf: the app
                // goes on capturing inside the app meanwhile — which the sentence says —
                // and moving where somebody's backups live is theirs to answer.
                if (rung.isProvisional) {
                    Text(
                        text = stringResource(Res.string.backup_folder_unreachable),
                        style = typography.bodySmall,
                        color = colorScheme.error,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("backup_folder_unreachable"),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Button(
                            onClick = {
                                onAction(BackupAction.ChooseFolder(platformContext))
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("backup_folder_reconnect"),
                        ) {
                            Text(
                                text = stringResource(Res.string.backup_folder_reconnect),
                                style = typography.labelLarge,
                            )
                        }

                        TextButton(
                            onClick = { onAction(BackupAction.KeepInsideApp) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.testTag("backup_folder_keep_in_app"),
                        ) {
                            Text(
                                text = stringResource(Res.string.backup_folder_keep_in_app),
                                style = typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * When the copy that has not happened yet will happen — read off the triggers that are
 * actually on, and never off a trigger the app does not have.
 *
 * The sentence used to promise two things at once: a copy "when you enter something", which
 * no trigger of this app does — every `DestructiveAction` is a deletion, a rewrite or a
 * restore — and one "the next time you open the app", which is the periodic trigger's and is
 * silent when that switch is off. The branch beside this one already refuses to make the
 * second promise for exactly that reason (see [LastBackupCard]), and a card cannot keep one
 * standard in one line and another in the next.
 *
 * With both triggers off what is left is the copy taken before a migration, which has no
 * switch of its own and rides on the vault (design D1) — the same occasion
 * [vaultSubtitle] names there, and the only one that would still produce this card's first
 * copy.
 */
@Composable
private fun neverHint(vault: VaultState): String = when {
    vault.isPeriodicOn && vault.isPreventiveOn ->
        stringResource(Res.string.backup_last_never_hint)

    vault.isPeriodicOn -> stringResource(Res.string.backup_last_never_hint_periodic)
    vault.isPreventiveOn -> stringResource(Res.string.backup_last_never_hint_preventive)
    else -> stringResource(Res.string.backup_last_never_hint_update_only)
}

/**
 * Which occasions produce a copy, said as occasions rather than as switches — and, with no
 * vault, the consequence of that instead: nothing is kept, and getting the data back is
 * down to the file the user exported (`local-backup` spec). The switch beside it already
 * shows off, so the subtitle spends its line on what off *means*.
 *
 * **The periodic occasion is named as the opening of the app, never as a cadence.** The
 * spec forbids this screen a promise of *"a cada N dias"* in as many words — no supported
 * platform lets an app keep one — and requires it to say instead that the copy happens when
 * the app is opened. This is the line that says it in the state a person is in for as long
 * as the vault works: the sheet behind the settings tile says the same thing at length, and
 * the sentence under the last-copy date says it only while no copy has been taken yet or
 * one has aged past the wait. The interval is still stated, because *after* how long is
 * half the occasion; what it is not allowed to be is the whole of it.
 */
@Composable
private fun vaultSubtitle(vault: VaultState): String {
    if (!vault.isOn) return stringResource(Res.string.backup_no_copies)

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
