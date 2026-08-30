@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.storedBackupActions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_history_action_delete
import com.neoutils.finsight.resources.backup_history_action_restore
import com.neoutils.finsight.resources.backup_history_action_share
import com.neoutils.finsight.resources.backup_history_action_share_subtitle
import com.neoutils.finsight.resources.backup_history_migration_subtitle
import com.neoutils.finsight.resources.backup_today
import com.neoutils.finsight.resources.backup_yesterday
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.sizeLabel
import com.neoutils.finsight.util.LocalDateFormats
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.stringResource

/**
 * What can be done with one kept copy: restored, handed to a place the user picks, or
 * removed.
 *
 * They are here rather than on the row because a list of copies is content and three
 * actions per row would be three affordances per row — and because two of the three are
 * consequential enough to be worth a deliberate second tap. Restoring replaces the whole
 * archive; removing takes away a copy that may be the only one holding something.
 *
 * Handing the file out is what gives a copy a way off the device. On Android the vault is
 * local and no cloud provider appears in a folder picker, so the copy already written is
 * the thing that has to be able to leave — and it leaves as it is, with no second capture.
 *
 * Nothing here reads the file. What the copy holds is read when it is actually reached for,
 * by the same gate the manual restore runs, which is where the confirmation gets its
 * numbers from (design D9).
 */
class StoredBackupActionsModal(
    private val backup: StoredBackup,
    private val onRestore: () -> Unit,
    private val onShare: () -> Unit,
    private val onRemove: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val dateFormats = LocalDateFormats.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_copy_actions"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                Text(
                    text = if (backup.name == PRE_MIGRATION_BACKUP_NAME) {
                        stringResource(Res.string.backup_history_migration_subtitle)
                    } else {
                        sizeLabel(backup.sizeInBytes)
                    },
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            ActionRow(
                icon = Icons.Outlined.Restore,
                title = stringResource(Res.string.backup_history_action_restore),
                subtitle = null,
                tone = colorScheme.onSurface,
                tag = "backup_copy_restore",
                onClick = onRestore,
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

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tone: Color,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        color = colorScheme.surfaceContainer,
        contentColor = tone,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Medium,
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
