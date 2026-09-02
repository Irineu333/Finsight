package com.neoutils.finsight.ui.modal.restoreWithoutCopy

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
import com.neoutils.finsight.resources.backup_no_copy_action
import com.neoutils.finsight.resources.backup_no_copy_message
import com.neoutils.finsight.resources.backup_no_copy_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

/**
 * The copy that would have made the restore reversible could not be taken, and the person
 * is the only one who may say to do it anyway.
 *
 * It comes up over the confirmation rather than in place of it, because the file being
 * restored is still the subject and the sheet underneath is what says which file. What this
 * one adds is the single fact that changed: the way back does not exist.
 *
 * Leaving without answering is answering no. The archive surviving is the default, and a
 * sheet dismissed by the scrim or the back gesture must not be read as permission to
 * destroy it.
 *
 * The warning it carries is the one the confirmation underneath already made, and it is
 * repeated here because this is the sheet where it stops being a caution and becomes the
 * whole of the situation: with no copy kept, there is nothing left that could bring the
 * archive back.
 *
 * @param reason why no copy could be taken, worded by the vault. This screen shows it and
 * decides nothing from it.
 */
class RestoreWithoutCopyModal(
    private val reason: UiText,
    private val onProceed: () -> Unit,
    private val onAbandon: () -> Unit,
) : ModalBottomSheet() {

    override fun onDismissed() {
        super.onDismissed()
        onAbandon()
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val modal = this@RestoreWithoutCopyModal

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.backup_no_copy_title),
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringUiText(reason),
                    style = typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            NoWayBackNotice()

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { manager.dismiss(modal) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_no_copy_cancel"),
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
                    onClick = onProceed,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_no_copy_action"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                ) {
                    Text(
                        text = stringResource(Res.string.backup_no_copy_action),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** What going ahead now costs: the restore stays irreversible, with nothing kept back. */
@Composable
private fun NoWayBackNotice() {
    Surface(
        color = Warning.copy(alpha = 0.14f),
        contentColor = Warning,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
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
                text = stringResource(Res.string.backup_no_copy_message),
                style = typography.bodySmall,
            )
        }
    }
}
