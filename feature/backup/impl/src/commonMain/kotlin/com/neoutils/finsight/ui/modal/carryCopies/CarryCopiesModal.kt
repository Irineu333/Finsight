package com.neoutils.finsight.ui.modal.carryCopies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_carry_action
import com.neoutils.finsight.resources.backup_carry_leave
import com.neoutils.finsight.resources.backup_carry_message
import com.neoutils.finsight.resources.backup_carry_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.copiesLabel
import org.jetbrains.compose.resources.stringResource

/**
 * The destination has just changed, and the copies already kept are still in the old one.
 *
 * **It is a question, and never a step that happens on the way past.** Nothing may move or
 * duplicate somebody's backups because a preference moved — the person is told how many
 * copies there are and where copying would leave them, and the run happens on their yes.
 *
 * **Leaving without answering leaves everything where it is**, which is the answer that
 * costs nothing: the copies stay in the destination they were written to, and pointing at it
 * again is what finds them (design D4). There is no state to be half in.
 *
 * The sentence says the two things a person needs to decide with, and both are promises the
 * carry keeps: only the newest copies the retention holds travel, and nothing is removed
 * from where it is (design D13).
 *
 * **Leaving is [onDeclined], not merely the absence of [onCarry].** A folder change keeps
 * the folder being left addressable for exactly one more change (task 11.10), and it is this
 * sheet's own dismissal — the "leave" button, the scrim, the swipe, the back press, all of
 * them alike — that says the person is done with the question and lets that folder be
 * forgotten. [onDismissed] is the one override point every way of leaving already funnels
 * through ([com.neoutils.finsight.ui.component.Modal.onDismissed]), so it is the one place
 * this needs to be caught rather than wired into every button.
 *
 * @param copies how many are in the destination being left, as the listing that offered this
 * counted them.
 * @param onDeclined called once, when the sheet is dismissed without [onCarry] having run —
 * never on the accepting path, where the carry itself is the answer.
 */
class CarryCopiesModal(
    private val copies: Int,
    private val onCarry: () -> Unit,
    private val onDeclined: () -> Unit,
) : ModalBottomSheet() {

    /**
     * Whether [onCarry] is the reason this sheet is being dismissed — the one case
     * [onDismissed] must not read as a decline, since carrying is itself the answer.
     */
    private var accepted = false

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val modal = this@CarryCopiesModal

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_carry_copies"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.backup_carry_title),
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.backup_carry_message, copiesLabel(copies)),
                    style = typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { manager.dismiss(modal) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_carry_leave"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.backup_carry_leave),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        accepted = true
                        manager.dismiss(modal)
                        onCarry()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_carry_action"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.backup_carry_action),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    override fun onDismissed() {
        super.onDismissed()
        if (!accepted) onDeclined()
    }
}
