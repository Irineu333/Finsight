@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.storedBackupActions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.restore.FileOrigin
import com.neoutils.finsight.domain.vault.KeptCopyFacts
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_transactions
import com.neoutils.finsight.resources.backup_copy_facts_accounts
import com.neoutils.finsight.resources.backup_copy_facts_origin
import com.neoutils.finsight.resources.backup_copy_facts_reading
import com.neoutils.finsight.resources.backup_copy_facts_size
import com.neoutils.finsight.resources.backup_copy_facts_unreadable
import com.neoutils.finsight.resources.backup_history_action_delete
import com.neoutils.finsight.resources.backup_history_action_restore
import com.neoutils.finsight.resources.backup_history_action_share
import com.neoutils.finsight.resources.backup_history_action_share_subtitle
import com.neoutils.finsight.resources.backup_history_migration_subtitle
import com.neoutils.finsight.resources.backup_today
import com.neoutils.finsight.resources.backup_yesterday
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.GroupGap
import com.neoutils.finsight.ui.screen.backup.RowGap
import com.neoutils.finsight.ui.screen.backup.TileShape
import com.neoutils.finsight.ui.screen.backup.ageLabel
import com.neoutils.finsight.ui.screen.backup.originLabel
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.sizeLabel
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * What one kept copy is, and what can be done with it: restored, handed to a place the user
 * picks, or removed.
 *
 * The actions are here rather than on the row because a list of copies is content and three
 * actions per row would be three affordances per row — and because two of the three are
 * consequential enough to be worth a deliberate second tap. Restoring replaces the whole
 * archive; removing takes away a copy that may be the only one holding something.
 *
 * Handing the file out is what gives a copy a way off the device. On Android the vault is
 * local and no cloud provider appears in a folder picker, so the copy already written is
 * the thing that has to be able to leave — and it leaves as it is, with no second capture.
 *
 * **This is where a copy is opened, and the only place a copy is opened to be described.**
 * The list shows what the file system says — a name, a stamp, a size — because the history
 * *is* the folder and reading every row would open one file per row (design D9). One tap is
 * one file, and the sheet is what asks for it: the person has stopped scanning and started
 * deciding about this copy, which is exactly when knowing what is inside it is worth the
 * read. The file is reached through [StoredBackup] and the destination's own contract;
 * nothing here learns a path into the folder (design D2).
 *
 * **It is laid out on the beat both backup screens use** (`BackupRows`): the same corner, the
 * same gap between the rows of one group, and the wider gap only where a group opens. Three
 * groups stand here — which copy this is, what it holds, and what can be done with it — so
 * the three actions read as one block rather than as three unrelated cards. Every box inside
 * the sheet is a step recessed from it, because a card the colour of the sheet it sits on is
 * not a card.
 *
 * @param facts the flow rather than a value, because a modal is built once and rendered by
 * the manager that holds it: what was read after the sheet went up would never reach it. The
 * sheet is put up before the read starts and never waits on it.
 */
class StoredBackupActionsModal(
    private val backup: StoredBackup,
    private val facts: StateFlow<KeptCopyFacts>,
    private val onRestore: () -> Unit,
    private val onShare: () -> Unit,
    private val onRemove: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val dateFormats = LocalDateFormats.current
        val clock = koinInject<Clock>()
        val held by facts.collectAsStateWithLifecycle()
        val isFromMigration = backup.name == PRE_MIGRATION_BACKUP_NAME

        // Once, when the sheet appears. The span under the stamp answers a question about
        // one copy at one moment, and one that ticked while it was read would be a
        // different answer every minute — the same reading the list takes for its rows.
        val now = remember(clock, backup) { clock.now() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_copy_actions"),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = dateFormats.formatDividerDate(
                        instant = backup.savedAt,
                        today = stringResource(Res.string.backup_today),
                        yesterday = stringResource(Res.string.backup_yesterday),
                    ) + ", " + dateFormats.formatInstantTime(backup.savedAt),
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                // How far back it reaches, which is what choosing between two copies is
                // about — the row that opened this sheet says the same thing, in the same
                // words. The copy kept before an update keeps its own sentence instead:
                // what it is for is the reason somebody went looking for it, and it wears
                // the amber the list marks it with.
                Text(
                    text = if (isFromMigration) {
                        stringResource(Res.string.backup_history_migration_subtitle)
                    } else {
                        ageLabel(backup.savedAt, now)
                    },
                    style = typography.bodyMedium,
                    color = if (isFromMigration) Warning else colorScheme.onSurfaceVariant,
                )
            }

            CopyFacts(
                facts = held,
                sizeInBytes = backup.sizeInBytes,
                modifier = Modifier.padding(top = GroupGap - RowGap),
            )

            ActionRow(
                icon = Icons.Outlined.Restore,
                title = stringResource(Res.string.backup_history_action_restore),
                subtitle = null,
                tone = colorScheme.onSurface,
                tag = "backup_copy_restore",
                onClick = onRestore,
                modifier = Modifier.padding(top = GroupGap - RowGap),
            )

            ActionRow(
                icon = Icons.Outlined.IosShare,
                title = stringResource(Res.string.backup_history_action_share),
                subtitle = stringResource(Res.string.backup_history_action_share_subtitle),
                tone = colorScheme.onSurface,
                tag = "backup_copy_share",
                onClick = onShare,
            )

            ActionRow(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(Res.string.backup_history_action_delete),
                subtitle = null,
                tone = colorScheme.error,
                tag = "backup_copy_delete",
                onClick = onRemove,
            )
        }
    }
}

/**
 * What the copy holds, as the file itself says it — and, while that is still being read or
 * could not be read at all, the one fact that never needed the file.
 *
 * The size is always there and always last, which is what keeps the box from being an empty
 * frame with a spinner in it: it is known from the listing, it is the least interesting of
 * the facts, and it is the one that cannot go missing. Everything above it comes out of the
 * copy — the stamp the capture wrote, and how much of the archive is in the file.
 *
 * **The frame is drawn before the facts arrive.** All four labels stand in every state, so
 * the box has one shape and the values fill into it: a bar where a value is still coming, a
 * dash where it never will. A box that grew by three rows the moment the file answered
 * would move the three actions under the reader's thumb.
 *
 * There is no *why* row. Which trigger took a copy is not recorded in the file (design D9,
 * and `snapshot_meta` carries a `formatVersion` for the day it is), so the sheet says the
 * one thing about a copy's purpose that is actually known: the copy kept before an update
 * names itself, and it does so above, where the list also says it.
 */
@Composable
private fun CopyFacts(
    facts: KeptCopyFacts,
    sizeInBytes: Long,
    modifier: Modifier = Modifier,
) {
    val held = facts as? KeptCopyFacts.Held

    // Nothing was read and nothing will be: the row keeps its label and says so with the
    // mark for an absent figure. While the file is still open, a value is coming and the
    // row shows that instead.
    val absent = if (facts is KeptCopyFacts.Unreadable) MissingValue else null

    Surface(
        color = colorScheme.background,
        shape = TileShape,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("backup_copy_facts"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FactRow(
                label = stringResource(Res.string.backup_copy_facts_origin),
                value = held?.let { originValue(it.origin) } ?: absent,
                tag = "backup_copy_facts_origin",
            )
            // Accounts and cards on one line, because they are read together: what a
            // person recognises is the shape of their own archive, not two figures.
            FactRow(
                label = stringResource(Res.string.backup_copy_facts_accounts),
                value = held?.let { "${it.counts.accounts} · ${it.counts.creditCards}" }
                    ?: absent,
                tag = "backup_copy_facts_accounts",
            )
            FactRow(
                label = stringResource(Res.string.backup_confirm_transactions),
                value = held?.counts?.transactions?.toString() ?: absent,
                tag = "backup_copy_facts_transactions",
            )

            FactRow(
                label = stringResource(Res.string.backup_copy_facts_size),
                value = sizeLabel(sizeInBytes),
                tag = "backup_copy_facts_size",
            )

            when (facts) {
                KeptCopyFacts.Reading -> Reading()
                KeptCopyFacts.Unreadable -> Unreadable()
                is KeptCopyFacts.Held -> Unit
            }
        }
    }
}

/** Which device wrote the file, and which build of the app — the two halves of one answer. */
@Composable
private fun originValue(origin: FileOrigin?): String {
    val where = originLabel(origin)

    // A build that states no version of its own stamps none, and none is shown.
    return if (origin != null && origin.appVersion.isNotBlank()) {
        "$where · v${origin.appVersion}"
    } else {
        where
    }
}

/**
 * One label and what stands opposite it: the figure, or the bar that says one is coming.
 *
 * The figures are set in tabular numerals so that four values stacked in one column line up
 * on their digits — a box of numbers that does not is what makes a set of facts read as a
 * dump of strings.
 */
@Composable
private fun FactRow(label: String, value: String?, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (value == null) {
            PendingValue()
        } else {
            Text(
                text = value,
                style = typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = TabularFigures,
                ),
                color = colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Where a value will be. It takes the height of the line it stands in, so the row it holds
 * open is exactly the row the figure will occupy.
 */
@Composable
private fun PendingValue() {
    Box(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .width(56.dp)
            .height(10.dp)
            .background(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            )
            .testTag("backup_copy_facts_pending"),
    )
}

/**
 * The file is being opened. It is said inside the box rather than in place of it, because
 * the size above is true already and replacing the whole box would take a fact away to show
 * that another one is coming.
 */
@Composable
private fun Reading() {
    Row(
        modifier = Modifier.testTag("backup_copy_facts_reading"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = colorScheme.onSurfaceVariant,
            strokeWidth = 1.5.dp,
        )
        Text(
            text = stringResource(Res.string.backup_copy_facts_reading),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The copy could not be opened, which is a thing that happens to a folder the user can also
 * reach with a file manager — and is not an error to put a dialog over.
 *
 * It is amber and not red for the reason the ageing vault is: nothing broke, and the other
 * copies are untouched. What it says is why, in the two ways it can be true, because they
 * are the two the person can act on: the file is gone, or it is damaged.
 */
@Composable
private fun Unreadable() {
    Row(
        modifier = Modifier.testTag("backup_copy_facts_unreadable"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = Warning,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(Res.string.backup_copy_facts_unreadable),
            style = typography.bodySmall,
            color = Warning,
        )
    }
}

/**
 * One thing this sheet can do, as a card of the same make as the row that opened it: the
 * corner the list uses, the padding the list uses, and a ground a step below the sheet.
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tone: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colorScheme.background,
        contentColor = tone,
        shape = TileShape,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** What stands where a figure would, on a copy that could not be opened to produce one. */
private const val MissingValue = "—"

/** Digits of one width, so a column of figures lines up on them. */
private const val TabularFigures = "tnum"
