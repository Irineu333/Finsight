@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.storedBackupActions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.vault.KeptCopyFacts
import com.neoutils.finsight.domain.vault.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_categories
import com.neoutils.finsight.resources.backup_confirm_transactions
import com.neoutils.finsight.resources.backup_copy_facts_accounts
import com.neoutils.finsight.resources.backup_copy_facts_captured
import com.neoutils.finsight.resources.backup_copy_facts_cards
import com.neoutils.finsight.resources.backup_copy_facts_origin
import com.neoutils.finsight.resources.backup_copy_facts_size
import com.neoutils.finsight.resources.backup_copy_facts_unreadable
import com.neoutils.finsight.resources.backup_history_action_delete
import com.neoutils.finsight.resources.backup_history_action_restore
import com.neoutils.finsight.resources.backup_history_action_share
import com.neoutils.finsight.resources.backup_history_action_share_subtitle
import com.neoutils.finsight.resources.backup_history_current_subtitle
import com.neoutils.finsight.resources.backup_history_migration_subtitle
import com.neoutils.finsight.resources.backup_today
import com.neoutils.finsight.resources.backup_yesterday
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.FactBox
import com.neoutils.finsight.ui.screen.backup.FactRow
import com.neoutils.finsight.ui.screen.backup.GroupGap
import com.neoutils.finsight.ui.screen.backup.MissingValue
import com.neoutils.finsight.ui.screen.backup.RowGap
import com.neoutils.finsight.ui.screen.backup.TileShape
import com.neoutils.finsight.ui.screen.backup.ageLabel
import com.neoutils.finsight.ui.screen.backup.originWithVersion
import com.neoutils.finsight.ui.screen.backup.sizeLabel
import com.neoutils.finsight.ui.theme.BackgroundTileRipple
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
 * @param isCurrent whether the archive in use already is this copy — a value and not a flow,
 * because it cannot change while the sheet is up: the only thing that moves it is a restore,
 * and a restore closes this sheet on its way to the confirmation.
 * @param facts the flow rather than a value, because a modal is built once and rendered by
 * the manager that holds it: what was read after the sheet went up would never reach it. The
 * sheet is put up before the read starts and never waits on it.
 */
class StoredBackupActionsModal(
    private val backup: StoredBackup,
    private val isCurrent: Boolean,
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
                // words. It stands on every copy here, the one kept before an update
                // included: the list row had one line to spend and had to choose, and this
                // sheet does not.
                Text(
                    text = ageLabel(backup.savedAt, now),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                // What the age cannot say, on the two copies that have something more to
                // say. The current one takes the line when a copy is both, exactly as the
                // list orders them: that the archive already *is* this copy settles what
                // restoring it would do, and nothing else on the sheet states it.
                when {
                    isCurrent -> Text(
                        text = stringResource(Res.string.backup_history_current_subtitle),
                        style = typography.bodyMedium,
                        color = colorScheme.primary,
                        modifier = Modifier.testTag("backup_copy_actions_current"),
                    )

                    isFromMigration -> Text(
                        text = stringResource(Res.string.backup_history_migration_subtitle),
                        style = typography.bodyMedium,
                        color = Warning,
                    )
                }
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
 * **The frame is drawn before the facts arrive.** Every label stands in every state, so the
 * box has one shape and the values fill into it: a bar where a value is still coming, a dash
 * where it never will. A box that grew by three rows the moment the file answered would move
 * the three actions under the reader's thumb.
 *
 * **The bars are the only thing that says a value is coming**, and the box is exactly as
 * tall while they stand as it is once the figures land. A spinner on a line of its own said
 * the same thing a second time and cost the rule above: the file answers in tens of
 * milliseconds — well inside the sheet's own entrance — so the row it added was removed
 * again while the sheet was still animating in, changing the content's height under an
 * animation whose target is computed from it.
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
    val dateFormats = LocalDateFormats.current

    // Nothing was read and nothing will be: the row keeps its label and says so with the
    // mark for an absent figure. While the file is still open, a value is coming and the
    // row shows that instead.
    val absent = if (facts is KeptCopyFacts.Unreadable) MissingValue else null

    FactBox(tag = "backup_copy_facts", modifier = modifier) {
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_origin),
            value = held?.let { originWithVersion(it.origin) } ?: absent,
            tag = "backup_copy_facts_origin",
        )
        // When the archive inside the file was actually read out of a running app, which is
        // not the same instant as the one above the box. That one is the file system's — when
        // this file arrived in this folder — and the two part company for every copy that
        // did not land here by being captured here: one carried across from another
        // destination, and one brought in from somewhere else entirely. The file's own stamp
        // is the only thing that says how old the data in it is.
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_captured),
            value = held?.origin?.let { origin ->
                dateFormats.formatDividerDate(
                    instant = origin.createdAt,
                    today = stringResource(Res.string.backup_today),
                    yesterday = stringResource(Res.string.backup_yesterday),
                ) + ", " + dateFormats.formatInstantTime(origin.createdAt)
            } ?: absent,
            tag = "backup_copy_facts_captured",
        )
        // A line each, like every other figure in this list. Sharing one put two numbers
        // under a label naming two things, and the reader had to pair them off by position
        // — the one row here where reading it meant decoding it.
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_accounts),
            value = held?.counts?.accounts?.toString() ?: absent,
            tag = "backup_copy_facts_accounts",
        )
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_cards),
            value = held?.counts?.creditCards?.toString() ?: absent,
            tag = "backup_copy_facts_cards",
        )
        FactRow(
            label = stringResource(Res.string.backup_confirm_transactions),
            value = held?.counts?.transactions?.toString() ?: absent,
            tag = "backup_copy_facts_transactions",
        )
        // Read out of every copy since the file has held one, and the fourth facade a
        // person recognises their own archive by — the restore confirmation counts it for
        // exactly that reason, and a sheet that dropped it was describing three quarters of
        // the same file.
        FactRow(
            label = stringResource(Res.string.backup_confirm_categories),
            value = held?.counts?.categories?.toString() ?: absent,
            tag = "backup_copy_facts_categories",
        )

        FactRow(
            label = stringResource(Res.string.backup_copy_facts_size),
            value = sizeLabel(sizeInBytes),
            tag = "backup_copy_facts_size",
        )

        if (facts is KeptCopyFacts.Unreadable) Unreadable()
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
 *
 * **[tone] colours the content and nothing else.** Material derives a card's press and
 * hover layer from the content colour in scope, so handing a tone to `Surface` as its
 * `contentColor` also repaints every tap in it: the destructive row washed `error` red
 * across the whole card while the two above it washed neutral, which is one card of three
 * flashing a different colour for a difference the icon and the label already state. The
 * ground decides the state layer — here that is `contentColorFor(background)`, which is
 * what the two neutral rows were asking for anyway — and the tone stays where it means
 * something, on the glyph and the label.
 *
 * How far that layer moves from rest is [BackgroundTileRipple]'s to say, not this row's: the
 * card sits on `background`, the darkest ground the dark scheme has, and Material's own
 * state-layer alphas over that ground read as the card replacing itself rather than
 * responding to a touch.
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
    BackgroundTileRipple {
        Surface(
            color = colorScheme.background,
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
                    tint = tone,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = typography.titleMedium,
                        color = tone,
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
}

