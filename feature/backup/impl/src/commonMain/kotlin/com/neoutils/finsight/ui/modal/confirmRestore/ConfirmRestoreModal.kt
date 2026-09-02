@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.confirmRestore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_action
import com.neoutils.finsight.resources.backup_confirm_cancel
import com.neoutils.finsight.resources.backup_confirm_categories
import com.neoutils.finsight.resources.backup_confirm_created
import com.neoutils.finsight.resources.backup_confirm_message
import com.neoutils.finsight.resources.backup_confirm_title
import com.neoutils.finsight.resources.backup_confirm_transactions
import com.neoutils.finsight.resources.backup_copy_facts_accounts
import com.neoutils.finsight.resources.backup_copy_facts_cards
import com.neoutils.finsight.resources.backup_copy_facts_origin
import com.neoutils.finsight.resources.backup_scope
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.FactBox
import com.neoutils.finsight.ui.screen.backup.FactRow
import com.neoutils.finsight.ui.screen.backup.GroupGap
import com.neoutils.finsight.ui.screen.backup.MissingValue
import com.neoutils.finsight.ui.screen.backup.OutcomeBox
import com.neoutils.finsight.ui.screen.backup.RowGap
import com.neoutils.finsight.ui.screen.backup.TileShape
import com.neoutils.finsight.ui.screen.backup.ageLabel
import com.neoutils.finsight.ui.screen.backup.originWithVersion
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The last thing between an approved file and an archive that is about to stop existing.
 *
 * It says which file, before it asks: what the file says about where it came from, and how
 * much it holds. That is the whole reason it is a sheet rather than a dialog with a sentence
 * in it — the operation replaces everything, and "restore a backup" is not a question anybody
 * can answer without knowing *which* backup.
 *
 * It also says what a backup holds and what it leaves behind — the preferences of this
 * install, which the file never carried and the restore will not touch. That sentence is owed
 * here rather than on the screen behind, because this is the last moment before the archive
 * is replaced and the only one at which anybody is asking the question.
 *
 * **Two screens put this up about two kinds of file, and it does not say the same thing about
 * both.** A copy the vault kept is this install's own archive at a known instant; a file from
 * the picker is a file, of an archive that may never have been this one. Which of the two is
 * being restored is [com.neoutils.finsight.domain.restore.RestoreSource], decided where it is
 * known and read here — see [ageSentence].
 *
 * **What it says about undoing depends on what the app will actually do.** With a copy of the
 * current archive kept first, the restore stops being a one-way door and the sheet says so;
 * with none kept, it says that too. The replacement is total either way — what changed is
 * whether there is something to come back to ([RestoreAftermath]).
 *
 * **It is laid out on the beat the rest of the feature uses** (`BackupRows`): the same corner,
 * the same gap between the rows of one group, the wider gap where a group opens, and every box
 * a step recessed from the sheet. What the file holds is stated in the fact box the sheet
 * about a kept copy states it in, row for row, because the two describe the same file and a
 * second layout for it was a second way of reading the same four counts.
 *
 * @param isRestoring the flow rather than a value, because a modal is built once and rendered
 * by the manager that holds it: a boolean passed in would still read false while the
 * replacement ran.
 * @param keepsCopy the flow, for the same reason — and because it moves while the sheet is up:
 * a copy that was owed and could not be taken turns this sheet's promise into a false one, so
 * the promise is dropped as it happens rather than left standing under the sheet that asks
 * whether to go on without it.
 * @param onDiscard called however the sheet was dismissed — the button, the scrim, the swipe.
 * The file this sheet is about is a copy nobody else owns, so leaving without an answer is
 * what removes it.
 */
class ConfirmRestoreModal(
    private val confirmation: RestoreConfirmation,
    private val isRestoring: StateFlow<Boolean>,
    private val keepsCopy: StateFlow<Boolean>,
    private val onConfirm: () -> Unit,
    private val onDiscard: () -> Unit,
) : ModalBottomSheet() {

    override fun onDismissed() {
        super.onDismissed()
        onDiscard()
    }

    /**
     * The sheet stays where it is while the replacement runs.
     *
     * There is nothing to call off — the swap is a single transaction — so a way out would
     * only take the spinner with it and leave the user watching the backup screen do nothing
     * in the middle of the one operation this app cannot undo. The same reasoning already
     * disables both buttons.
     */
    @Composable
    override fun isDismissible(): Boolean {
        val restoring by isRestoring.collectAsStateWithLifecycle()
        return !restoring
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val modal = this@ConfirmRestoreModal
        val restoring by isRestoring.collectAsStateWithLifecycle()
        val reversible by keepsCopy.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_restore_confirm_sheet"),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.backup_confirm_title),
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.backup_confirm_message),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            FileFacts(
                confirmation = confirmation,
                modifier = Modifier.padding(top = GroupGap - RowGap),
            )

            HowFarBack(confirmation)

            // What the file carries, and what it deliberately does not, said here because
            // this is the moment the difference between "my data" and "my app as I left it"
            // would otherwise be discovered (`local-backup` spec).
            Text(
                text = stringResource(Res.string.backup_scope),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = GroupGap - RowGap)
                    .testTag("backup_restore_confirm_scope"),
            )

            Aftermath(reversible)

            Row(modifier = Modifier.padding(top = GroupGap - RowGap).fillMaxWidth()) {
                OutlinedButton(
                    onClick = { manager.dismiss(modal) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_restore_confirm_cancel"),
                    enabled = !restoring,
                    shape = TileShape,
                ) {
                    Text(
                        text = stringResource(Res.string.backup_confirm_cancel),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_restore_confirm_action"),
                    // Both, and not only this one: the replacement is a single transaction,
                    // so there is nothing left to call off once it starts.
                    enabled = !restoring,
                    shape = TileShape,
                    // Red in both states. Reversible is not harmless: the replacement is
                    // total, and the way back is another restore rather than an undo.
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                ) {
                    if (restoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colorScheme.onError,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.backup_confirm_action),
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which file this is: when it was written, what wrote it, and how much of an archive is in
 * it.
 *
 * It is the sheet about a kept copy's box, with its rows in its order, and one row fewer: a
 * candidate is a copy this app made of the file somewhere it owns, so its size on disk says
 * nothing about the file the user chose. Everything else is the same four facades, because
 * the two sheets describe the same file and the person may well have read the other one a tap
 * ago.
 *
 * A file with no stamp keeps its counts and says so where the stamp would have been. Three
 * rows reading "unknown" would be noise dressed as information; two rows carrying the mark
 * for an absent value are the honest shape of a file that said nothing about itself.
 */
@Composable
private fun FileFacts(confirmation: RestoreConfirmation, modifier: Modifier = Modifier) {
    val dateFormats = LocalDateFormats.current
    val origin = confirmation.origin
    val counts = confirmation.counts

    FactBox(tag = "backup_restore_confirm_facts", modifier = modifier) {
        FactRow(
            label = stringResource(Res.string.backup_confirm_created),
            value = origin?.let {
                dateFormats.formatInstantDate(it.createdAt) + ", " +
                    dateFormats.formatInstantTime(it.createdAt)
            } ?: MissingValue,
            tag = "backup_restore_confirm_created",
        )
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_origin),
            value = originWithVersion(origin),
            tag = "backup_restore_confirm_origin",
        )
        // A line each, like every other figure in this list. Sharing one put two numbers
        // under a label naming two things, and the reader had to pair them off by position
        // — the one row here where reading it meant decoding it.
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_accounts),
            value = counts.accounts.toString(),
            tag = "backup_restore_confirm_accounts",
        )
        FactRow(
            label = stringResource(Res.string.backup_copy_facts_cards),
            value = counts.creditCards.toString(),
            tag = "backup_restore_confirm_cards",
        )
        FactRow(
            label = stringResource(Res.string.backup_confirm_transactions),
            value = counts.transactions.toString(),
            tag = "backup_restore_confirm_transactions",
        )
        FactRow(
            label = stringResource(Res.string.backup_confirm_categories),
            value = counts.categories.toString(),
            tag = "backup_restore_confirm_categories",
        )
    }
}

/**
 * How far back the file reaches, and — where the app is entitled to say it — what restoring
 * it therefore costs.
 *
 * The stamp alone does not answer the question being asked here. "12 ago, 09:15" is a fact
 * about the file; what it *costs* is the span between then and now, and this is the last
 * screen before that cost is paid. So the span is said in words, under the box, rather than
 * left to be worked out against today's date.
 *
 * Which sentence carries it is [ageSentence]'s, and a file with no stamp gets none: there is
 * no span to state and nothing here invents one.
 *
 * It is measured against the moment the sheet was built and not re-read while it is up: a
 * confirmation whose numbers move under the reader is a confirmation about a different file
 * every second.
 */
@Composable
private fun HowFarBack(confirmation: RestoreConfirmation) {
    val clock = koinInject<Clock>()
    val sentence = ageSentence(confirmation) ?: return
    val createdAt = confirmation.origin?.createdAt ?: return

    // Once, when the sheet appears: the reader is deciding about one file at one moment, and
    // a span that ticked while they read it would be a different question each frame.
    val now = remember(clock, confirmation) { clock.now() }

    Text(
        text = stringResource(sentence, ageLabel(createdAt, now)),
        style = typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = colorScheme.onSurface,
        modifier = Modifier.testTag("backup_restore_confirm_age"),
    )
}

/**
 * What is left of the archive afterwards — one box, in the two states it can be in.
 *
 * One box and not two, for the reason the settings sheet gives: the states are told apart by
 * their id, so a branch between two boxes would put both names in the tree while they crossed.
 * The crossing is real here rather than theoretical — a copy that was owed and could not be
 * taken moves this from the promise to the warning while the sheet is standing.
 *
 * The accent is the one the copies list marks the current archive with, and the amber is the
 * one the feature uses wherever nothing is broken but something is not covered.
 */
@Composable
private fun Aftermath(keepsCopy: Boolean) {
    val aftermath = restoreAftermath(keepsCopy)
    val tone = if (keepsCopy) colorScheme.primary else Warning

    OutcomeBox(
        value = stringResource(aftermath.title),
        hint = aftermath.hint(),
        tone = tone,
        container = tone.copy(alpha = 0.14f),
        tag = when (aftermath) {
            RestoreAftermath.COPY_KEPT -> "backup_restore_confirm_reversible"
            RestoreAftermath.NO_COPY -> "backup_restore_confirm_irreversible"
        },
    )
}
