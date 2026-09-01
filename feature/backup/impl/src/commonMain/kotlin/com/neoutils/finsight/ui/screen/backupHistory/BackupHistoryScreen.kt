@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backupHistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.extension.LocalPlatformContext
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.extension.currentYearMonth
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_destination_title
import com.neoutils.finsight.resources.backup_history_capture
import com.neoutils.finsight.resources.backup_history_current_label
import com.neoutils.finsight.resources.backup_history_current_subtitle
import com.neoutils.finsight.resources.backup_history_empty_message
import com.neoutils.finsight.resources.backup_history_empty_off
import com.neoutils.finsight.resources.backup_history_empty_title
import com.neoutils.finsight.resources.backup_history_failed
import com.neoutils.finsight.resources.backup_history_failed_title
import com.neoutils.finsight.resources.backup_history_import
import com.neoutils.finsight.resources.backup_history_migration_label
import com.neoutils.finsight.resources.backup_history_migration_subtitle
import com.neoutils.finsight.resources.backup_history_newest_label
import com.neoutils.finsight.resources.backup_history_summary
import com.neoutils.finsight.resources.backup_history_title
import com.neoutils.finsight.resources.backup_today
import com.neoutils.finsight.resources.backup_yesterday
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.confirmRemoveCopy.ConfirmRemoveCopyModal
import com.neoutils.finsight.ui.modal.confirmRestore.ConfirmRestoreModal
import com.neoutils.finsight.ui.modal.restoreWithoutCopy.RestoreWithoutCopyModal
import com.neoutils.finsight.ui.modal.storedBackupActions.StoredBackupActionsModal
import com.neoutils.finsight.ui.modal.vaultDestination.VaultDestinationModal
import com.neoutils.finsight.ui.screen.backup.GroupGap
import com.neoutils.finsight.ui.screen.backup.RowGap
import com.neoutils.finsight.ui.screen.backup.TabularFigures
import com.neoutils.finsight.ui.screen.backup.TileShape
import com.neoutils.finsight.ui.screen.backup.ageLabel
import com.neoutils.finsight.ui.screen.backup.backupRows
import com.neoutils.finsight.ui.screen.backup.copiesLabel
import com.neoutils.finsight.ui.screen.backup.destinationLabel
import com.neoutils.finsight.ui.screen.backup.sizeLabel
import com.neoutils.finsight.ui.theme.BackgroundTileRipple
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.YearMonth
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
 * **It is content, and the backup screen is configuration** — so its rows are denser than a
 * settings tile and carry a mark of their own. What the two screens keep in common is the
 * beat they are laid out on, which is stated once, next to the backup screen that also
 * reads it (`BackupRows`).
 *
 * **It is where the copies live, and the backup screen is whether they happen.** That line
 * is what puts three things on this screen and not on the other one: where they are kept,
 * which is chosen from the header that already names it; a copy taken now; and a file
 * brought in from elsewhere. The backup screen keeps the switch, the triggers, and the two
 * operations that leave the app by a picker.
 *
 * **The header and the two actions stand in every state**, above the branch that draws the
 * copies — while the folder is being read, while it holds nothing, and while it cannot be
 * read at all. Each of those is a state somebody arrives in *wanting* one of them: a folder
 * with nothing in it is exactly when a person reaches for the import, and a destination that
 * will not answer is when they reach for the selector.
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
    val clock = koinInject<Clock>()
    val modalManager = LocalModalManager.current
    val platformContext = LocalPlatformContext.current

    // Read once: the year is what decides whether a month heading has to carry one, and a
    // list that is being scrolled must not ask the clock again for every heading. The
    // instant is read once for the same reason and for one more — every row's "how long
    // ago" has to be measured against the same present, or two rows taken a minute apart
    // could be read as the same age.
    val thisYear = remember(clock) { clock.currentYearMonth().year }
    val now = remember(clock) { clock.now() }

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

    ConfirmRemoveHost(
        backup = uiState.pendingRemoval,
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
                    actionIconContentColor = colorScheme.onBackground,
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            DestinationHeader(
                where = destinationLabel(uiState.destination, uiState.folderPath),
                // Only once the destination has answered. A summary built before that would
                // be counting a folder nobody has read, and "no copies" is an answer there
                // is none to give yet (design D9).
                summary = uiState.copies
                    .takeIf { !uiState.isLoading && !uiState.isUnreadable }
                    ?.let { copies ->
                        stringResource(
                            Res.string.backup_history_summary,
                            copiesLabel(copies.size),
                            sizeLabel(uiState.totalBytes),
                        )
                    },
                onChange = if (uiState.isFolderOffered) {
                    {
                        modalManager.show(
                            VaultDestinationModal(
                                selected = uiState.destination,
                                onChooseFolder = {
                                    viewModel.onAction(
                                        BackupHistoryAction.ChooseFolder(platformContext)
                                    )
                                },
                                onKeepInsideApp = {
                                    viewModel.onAction(BackupHistoryAction.KeepInsideApp)
                                },
                            )
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            Actions(
                uiState = uiState,
                platformContext = platformContext,
                onAction = viewModel::onAction,
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("backup_history_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.copies.isEmpty() -> Empty(
                    isUnreadable = uiState.isUnreadable,
                    message = when {
                        uiState.isUnreadable -> stringResource(Res.string.backup_history_failed)
                        uiState.isVaultOn -> stringResource(Res.string.backup_history_empty_message)
                        else -> stringResource(Res.string.backup_history_empty_off)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("backup_history_list"),
                    // The first month heading opens a group like every other one, and
                    // `BackupRows` deliberately gives the first row of a list no leading gap
                    // — so the beat between the actions above and the copies below is set
                    // here, to exactly what the headings after it take.
                    contentPadding = PaddingValues(top = GroupGap - RowGap, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    backupRows {
                        uiState.copies
                            .groupBy { it.savedAt.toYearMonth() }
                            .forEach { (month, copies) ->
                                row(key = "month_$month", opensGroup = true) { modifier ->
                                    MonthTitle(
                                        month = month,
                                        thisYear = thisYear,
                                        modifier = modifier,
                                    )
                                }

                                copies.forEach { copy ->
                                    row(key = copy.name) { modifier ->
                                        StoredBackupRow(
                                            backup = copy,
                                            isCurrent = uiState.isCurrent(copy),
                                            isNewest = uiState.isNewest(copy),
                                            now = now,
                                            isWorking = uiState.working == copy,
                                            enabled = !uiState.isBusy,
                                            modifier = modifier,
                                            onClick = {
                                                // The read starts and the sheet goes up in
                                                // the same breath, in that order and without
                                                // waiting: the file is opened for the copy
                                                // that was tapped, and the sheet fills in
                                                // when it answers.
                                                viewModel.onAction(
                                                    BackupHistoryAction.Inspect(copy)
                                                )
                                                modalManager.show(
                                                    StoredBackupActionsModal(
                                                        backup = copy,
                                                        isCurrent = uiState.isCurrent(copy),
                                                        facts = viewModel.facts,
                                                        onRestore = {
                                                            modalManager.dismiss()
                                                            viewModel.onAction(
                                                                BackupHistoryAction.Restore(copy)
                                                            )
                                                        },
                                                        onShare = {
                                                            modalManager.dismiss()
                                                            viewModel.onAction(
                                                                BackupHistoryAction.Share(
                                                                    backup = copy,
                                                                    context = platformContext,
                                                                )
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
        }
    }
}

/**
 * The two things this screen does that are not about a copy already in it: take another,
 * and bring one in.
 *
 * **They are labelled, side by side, and in the list rather than in the bar.** A glyph alone
 * in a top bar is a control nobody finds: it has no word on it, it sits where a screen's
 * navigation lives rather than where its subject does, and a bare `+` over a list of files
 * says *add* without saying what. Two of them side by side would have been twice the
 * ambiguity — take a copy and bring one in are precisely the two things a `+` could mean.
 * So each carries its own sentence, and the pair reads as what the screen offers.
 *
 * They sit under the destination and above the copies because that is the order the screen
 * is read in: where the copies are kept, what can be done about that, and then what is
 * there. It is also what keeps them reachable on a folder holding nothing at all, which is
 * exactly when somebody is looking for one of them.
 *
 * **A capture is offered while there is a vault to write into**, and so is an import: with
 * the vault off nothing lands in the destination at all (design D1). Both refuse in the
 * vault's own words rather than the screen's, and the screen simply does not offer a control
 * whose only answer would be that refusal.
 *
 * While either runs, a spinner takes its glyph's place and the same
 * [BackupHistoryUiState.isBusy] that stills the rows stops a second press — of it or of the
 * other, because both end by rearranging the same folder.
 */
@Composable
private fun Actions(
    uiState: BackupHistoryUiState,
    platformContext: PlatformContext,
    onAction: (BackupHistoryAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        ActionTile(
            icon = Icons.Outlined.AddCircleOutline,
            // The vault skips a capture while the copy it already has still holds
            // everything the archive does, which is what stops a run of deletions leaving a
            // folder of identical files — and it is the wrong answer to somebody who has
            // just pressed a button. `BackupVault.captureNow` is the intent without that
            // comparison, and it is where the difference lives so that no screen has to
            // know about it.
            label = stringResource(Res.string.backup_history_capture),
            isRunning = uiState.isCapturing,
            enabled = uiState.isVaultOn && !uiState.isBusy,
            tag = "backup_history_capture",
            onClick = { onAction(BackupHistoryAction.Capture) },
            modifier = Modifier.weight(1f),
        )

        ActionTile(
            icon = Icons.Outlined.FileOpen,
            label = stringResource(Res.string.backup_history_import),
            isRunning = uiState.isImporting,
            enabled = uiState.isVaultOn && !uiState.isBusy,
            tag = "backup_history_import",
            onClick = { onAction(BackupHistoryAction.Import(platformContext)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One of the two, as a card of the same make as the rows below it: the corner the list
 * wears, the ground the list wears, and a label that says what pressing it does.
 *
 * The glyph is the only thing the spinner replaces. A control whose label vanished while it
 * ran would be a second thing to read on the frame the person is waiting on, and the label
 * is what identifies which of the two is busy.
 *
 * How far the state layer moves from rest is [BackgroundTileRipple]'s to say, not this
 * tile's: Material's own state-layer alphas painted white over `surfaceContainer`, in the
 * dark scheme, read as the card replacing itself rather than responding to a touch.
 */
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    isRunning: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant

    BackgroundTileRipple {
        Surface(
            color = colorScheme.surfaceContainer,
            shape = TileShape,
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.testTag(tag),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = tone,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = label,
                    style = typography.labelLarge,
                    color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Where the copies are kept, what they add up to, and the way to keep them somewhere else.
 *
 * **It is the header and the selector at once, and that is the whole of why the choice moved
 * here.** The question "where do my copies live" has one answer on one screen, said in the
 * place the answer is already given rather than in a settings row two screens away that
 * repeated it. Tapping it opens the two rungs side by side with what each does *not* cover,
 * which is where that comparison belongs — at the moment of choosing (design D3).
 *
 * Where is the folder's own name, once the destination has answered with one — a display
 * name is not a handle, so naming it is not what design D2 forbids: a folder is still never
 * a security-scoped handle on iOS or a tree `Uri` on Android, neither of which survives being
 * turned into text, but the word a person reads on a folder in Files or in a file manager is
 * neither. What falls back to the rung's own sentence is everything else — nothing pointed
 * at yet, a platform that cannot currently say, or the app's own storage, which has no name
 * to give.
 *
 * **The summary is absent until the destination has answered**, rather than reading zero: a
 * count over a folder nothing has read would be design D9's forbidden sentence at the top of
 * the screen that is most about it.
 *
 * How far the state layer moves from rest is [BackgroundTileRipple]'s to say, not this
 * card's: Material's own state-layer alphas painted white over `surfaceContainer`, in the
 * dark scheme, read as the card replacing itself rather than responding to a touch.
 *
 * @param onChange null on a platform that cannot raise a folder picker at all, where there
 * is one rung and therefore nothing to choose between. It is not a judgement about folders
 * or providers, which the app never makes (design D16).
 */
@Composable
private fun DestinationHeader(
    where: String,
    summary: String?,
    onChange: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BackgroundTileRipple {
        Surface(
            color = colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .testTag("backup_history_destination"),
        ) {
            Row(
                // The whole card is the target where there is one, so the eye sees one control
                // where a screen reader announces one — and where there is nothing to choose
                // between, it is a header and is announced as one rather than as a button
                // nobody may press.
                modifier = Modifier
                    .then(
                        if (onChange != null) {
                            Modifier.clickable(role = Role.Button, onClick = onChange)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // The question the card answers, written out, so that somebody looking for
                    // where their copies are kept finds the words they were looking for.
                    Text(
                        text = stringResource(Res.string.backup_destination_title),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = where,
                        style = typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    if (summary != null) {
                        Text(
                            text = summary,
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            // Tagged on the line that renders the count, not on the card
                            // that holds it: the card is the control a flow taps, and it
                            // reaches an E2E driver carrying no text of its own, so a
                            // figure asserted on it could only ever be asserted as
                            // present (`.maestro/README.md` §5.2, padrão 2). Absent until
                            // the destination has answered, which is the same condition
                            // the summary itself is under.
                            modifier = Modifier.testTag("backup_history_summary"),
                        )
                    }
                }
                if (onChange != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The month a run of copies belongs to.
 *
 * The month and not the day: the vault takes a copy every few days, so a heading per day
 * would be a heading per row and would group nothing. The year is left out while it is the
 * current one — the same economy `formatRelativeDate` makes — and written where it changes
 * the meaning.
 */
@Composable
private fun MonthTitle(month: YearMonth, thisYear: Int, modifier: Modifier = Modifier) {
    val dateFormats = LocalDateFormats.current

    Text(
        text = if (month.year == thisYear) {
            dateFormats.monthName(month.month)
        } else {
            dateFormats.yearMonth.format(month)
        },
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(vertical = 4.dp)
            .padding(horizontal = 4.dp)
            .testTag("backup_history_month"),
    )
}

/**
 * One copy, said in the least that lets it be recognised without being opened: when it was
 * taken, how far back that is, how big it is, and — for the one taken before a migration —
 * what it is for.
 *
 * **How far back is the line that decides anything.** The stamp says *when*; choosing
 * between two copies is choosing how much of what was typed since is lost, and that is a
 * span rather than a date. The date stays, because a person recognises their own copy by
 * it; the span is what is read.
 *
 * **One mark, and the current copy takes it.** A row can be several things at once — the
 * one the app is running on, the newest one, the one kept before an update — and stacking
 * three tags on it would say less than one does. *Current* wins because it is the only one
 * the list could not say before: it is where the person is standing, whether the copy was
 * just taken or the archive was restored from it. Then the update copy, which is the one
 * somebody comes here looking for; then the newest, which the top of a newest-first list
 * half says already.
 *
 * That copy is labelled in amber rather than in the accent, for the reason the backup
 * screen marks an ageing vault in amber: it is the copy somebody goes looking for when a
 * figure stopped adding up after an update, and it is the one retention never counts
 * (design D10). The current one is the accent, because nothing is wrong with it — it is
 * simply where things are.
 *
 * Nothing else about a copy is shown, because nothing else is known without reading the
 * file, and the file is read when it is reached for. **The row is the whole target**, and it
 * carries no mark of its own for that: a glyph on the right would promise a menu that
 * belongs to it, when what opens is the sheet the whole row opens. What stands there is the
 * spinner, while this copy is the one being worked on.
 *
 * How far the state layer moves from rest is [BackgroundTileRipple]'s to say, not this row's
 * — on `surfaceContainer` and equally on the current copy's tinted ground, since Material
 * derives the ripple from whichever colour is actually on screen: Material's own state-layer
 * alphas painted white over either, in the dark scheme, read as the row replacing itself
 * rather than responding to a touch.
 */
@Composable
private fun StoredBackupRow(
    backup: StoredBackup,
    isCurrent: Boolean,
    isNewest: Boolean,
    now: Instant,
    isWorking: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dateFormats = LocalDateFormats.current
    val isFromMigration = backup.name == PRE_MIGRATION_BACKUP_NAME

    BackgroundTileRipple {
        Surface(
            color = if (isCurrent) {
                colorScheme.primary.copy(alpha = 0.10f)
            } else {
                colorScheme.surfaceContainer
            },
            shape = TileShape,
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .testTag("backup_copy"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = when {
                        isCurrent -> colorScheme.primary
                        isFromMigration -> Warning
                        else -> colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = dateFormats.formatDividerDate(
                            instant = backup.savedAt,
                            today = stringResource(Res.string.backup_today),
                            yesterday = stringResource(Res.string.backup_yesterday),
                        ) + ", " + dateFormats.formatInstantTime(backup.savedAt),
                        style = typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    // Set in tabular figures, so that a column of spans and sizes lines up on
                    // its digits: the line is read down the list rather than row by row.
                    Text(
                        text = ageLabel(backup.savedAt, now) + " · " + sizeLabel(backup.sizeInBytes),
                        style = typography.bodySmall.copy(
                            fontFeatureSettings = TabularFigures,
                        ),
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("backup_copy_age"),
                    )
                    // The second line is spent on whichever of the two this row has to say,
                    // and the current copy's takes it: a row saying where the app is standing
                    // is answering the question somebody opened this screen with.
                    if (isCurrent) {
                        Text(
                            text = stringResource(Res.string.backup_history_current_subtitle),
                            style = typography.bodySmall,
                            color = colorScheme.primary,
                            modifier = Modifier.testTag("backup_copy_current_subtitle"),
                        )
                    } else if (isFromMigration) {
                        Text(
                            text = stringResource(Res.string.backup_history_migration_subtitle),
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    isCurrent -> CurrentTag()
                    isFromMigration -> MigrationTag()
                    isNewest -> NewestTag()
                }

                if (isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

/** What marks the copy taken before a migration out of the run around it. */
@Composable
private fun MigrationTag() = RowTag(
    text = stringResource(Res.string.backup_history_migration_label),
    tone = Warning,
    modifier = Modifier.testTag("backup_copy_migration_label"),
)

/**
 * What marks the last copy the archive was identical to — the one to read the others
 * against, and the answer to a question this screen would otherwise be silent about: which
 * of these the app is standing on after an older one was restored.
 *
 * **It says *was* identical, and that is the whole of what is known.** The pointer behind
 * it ([ArchiveCopy][com.neoutils.finsight.domain.vault.ArchiveCopy]) is written the moment
 * a copy is taken or restored from, and nothing entered afterwards moves it — no reading of
 * the live archive says which file it came from, so there is nothing to move it with. A tag
 * claiming the app's data *is* this copy's would therefore stop being true at the first
 * transaction somebody enters and go on being shown, which is the shape of every other
 * false promise this feature exists not to make.
 */
@Composable
private fun CurrentTag() = RowTag(
    text = stringResource(Res.string.backup_history_current_label),
    tone = colorScheme.primary,
    modifier = Modifier.testTag("backup_copy_current_label"),
)

/**
 * What marks the newest copy, on the rows where that is not already said.
 *
 * It is only ever on a row that is not the current one, which is exactly when it carries
 * information: the person is standing on an older copy, and this is how much further
 * forward the folder goes.
 */
@Composable
private fun NewestTag() = RowTag(
    text = stringResource(Res.string.backup_history_newest_label),
    tone = colorScheme.onSurfaceVariant,
    modifier = Modifier.testTag("backup_copy_newest_label"),
)

/**
 * The one shape a row's mark takes, so three marks cannot become three shapes.
 *
 * The tag stays at the call site rather than being passed in as text: an `id` a flow
 * reaches an element by is a `testTag` somebody has to be able to find by searching for
 * one (`.maestro/README.md`).
 */
@Composable
private fun RowTag(text: String, tone: Color, modifier: Modifier = Modifier) {
    Surface(
        color = tone.copy(alpha = 0.15f),
        contentColor = tone,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * Nothing kept yet — which is not an error and is not a list of zero items. It says when the
 * first copy happens, or that nothing will be kept until the vault is turned on.
 *
 * **A destination that could not be read is the other state, whole.** The headline and the
 * glyph change with the sentence rather than staying behind it: an empty box over "no copies
 * yet" says the folder is empty, which is a claim nothing read, and the two lead to
 * different actions — nothing yet means wait, cannot read means go and look
 * ([BackupHistoryUiState.isUnreadable]).
 */
@Composable
private fun Empty(isUnreadable: Boolean, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp)
                .testTag("backup_history_empty"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isUnreadable) {
                    Icons.Outlined.FolderOff
                } else {
                    Icons.Outlined.Inventory2
                },
                contentDescription = null,
                tint = colorScheme.outlineVariant,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(
                    if (isUnreadable) {
                        Res.string.backup_history_failed_title
                    } else {
                        Res.string.backup_history_empty_title
                    }
                ),
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

/**
 * Puts the removal question up while there is one, and takes it down when there is not —
 * the same arrangement the two restore sheets use, because a modal is rendered by the
 * manager outside this tree and the state is what keeps the two in step.
 */
@Composable
private fun ConfirmRemoveHost(
    backup: StoredBackup?,
    onAction: (BackupHistoryAction) -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (backup != null) {
        val modal = remember(backup) {
            ConfirmRemoveCopyModal(
                backup = backup,
                onConfirm = { onAction(BackupHistoryAction.ConfirmRemove) },
                onAbandon = { onAction(BackupHistoryAction.AbandonRemoval) },
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
