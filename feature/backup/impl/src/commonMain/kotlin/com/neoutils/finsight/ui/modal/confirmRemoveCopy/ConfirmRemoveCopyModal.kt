@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.confirmRemoveCopy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_cancel
import com.neoutils.finsight.resources.backup_remove_confirm_action
import com.neoutils.finsight.resources.backup_remove_confirm_message
import com.neoutils.finsight.resources.backup_remove_confirm_migration
import com.neoutils.finsight.resources.backup_remove_confirm_title
import com.neoutils.finsight.resources.backup_today
import com.neoutils.finsight.resources.backup_yesterday
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.stringResource

/**
 * The last thing between a kept copy and its being gone.
 *
 * A removal is the one action on this screen that destroys something and offers nothing in
 * its place: the copy is not moved to a bin, the history is a reading of the folder rather
 * than a record, and there is no undo anywhere in the feature. Every other destructive
 * action in the app is confirmed, and one tap on a list of backups is exactly where a slip
 * costs the most.
 *
 * **It says which copy, in the words the row and the sheet before it used.** "Delete this
 * copy" is not a question anybody can answer without knowing *which* copy — the same reason
 * the restore is confirmed with the file's own facts in front of it
 * ([com.neoutils.finsight.ui.modal.confirmRestore.ConfirmRestoreModal]).
 *
 * **The copy taken before an update says what it is.** Retention is told never to sweep it,
 * because the damage it exists to undo is found out days later — but a person in front of
 * the screen may still take it away, and the app does not hold files somebody could delete
 * with a file manager anyway (design D9). What it owes them is the one fact the date does
 * not carry, so the sentence stands here instead of a control that is greyed out for a
 * reason nobody is told.
 *
 * Leaving without answering is answering no. The copy surviving is the default, and a sheet
 * dismissed by the scrim or the back gesture must not be read as permission to remove it —
 * the shape [com.neoutils.finsight.ui.modal.restoreWithoutCopy.RestoreWithoutCopyModal]
 * already gives a destructive question in this feature, followed here rather than invented
 * again.
 */
class ConfirmRemoveCopyModal(
    private val backup: StoredBackup,
    private val onConfirm: () -> Unit,
    private val onAbandon: () -> Unit,
) : ModalBottomSheet() {

    override fun onDismissed() {
        super.onDismissed()
        onAbandon()
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val modal = this@ConfirmRemoveCopyModal
        val dateFormats = LocalDateFormats.current

        // The stamp the row and the actions sheet showed, spelled the same way: the person
        // is being asked about the copy they just tapped, and a second wording for the same
        // instant is a second copy as far as anybody reading it is concerned.
        val taken = dateFormats.formatDividerDate(
            instant = backup.savedAt,
            today = stringResource(Res.string.backup_today),
            yesterday = stringResource(Res.string.backup_yesterday),
        ) + ", " + dateFormats.formatInstantTime(backup.savedAt)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_remove_confirm_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.backup_remove_confirm_title),
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.backup_remove_confirm_message, taken),
                    style = typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            if (backup.name == PRE_MIGRATION_BACKUP_NAME) FromUpdateNotice()

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { manager.dismiss(modal) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_remove_confirm_cancel"),
                    shape = RoundedCornerShape(12.dp),
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
                        .testTag("backup_remove_confirm_action"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                ) {
                    Text(
                        text = stringResource(Res.string.backup_remove_confirm_action),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * What this one copy is, on the one copy the app itself would never take away.
 *
 * Amber and not red for the reason the rest of the feature uses it: nothing is broken and
 * nothing is being refused — this is a fact the date cannot carry, said before the person
 * decides.
 */
@Composable
private fun FromUpdateNotice() {
    Surface(
        color = Warning.copy(alpha = 0.14f),
        contentColor = Warning,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_remove_confirm_migration"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.backup_remove_confirm_migration),
                style = typography.bodySmall,
            )
        }
    }
}
