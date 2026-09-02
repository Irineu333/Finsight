package com.neoutils.finsight.feature.backup.api

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_cancel
import com.neoutils.finsight.resources.backup_no_copy_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The copy that would have made a destructive action reversible could not be taken, and the
 * person is the only one who may say to do it anyway.
 *
 * It is [CaptureRefusal]'s rendering, and it lives beside it for the same reason: the sheet
 * every feature puts over its own confirmation is one sheet, so what it says and what a
 * dismissal means are decided once.
 *
 * It comes up **over** the confirmation rather than in place of it: the sheet underneath is
 * what says what is going, and this adds the single fact that changed — there is nothing
 * kept back.
 *
 * Leaving without answering is answering no. What the action would destroy surviving is the
 * default, and a sheet dismissed by the scrim or the back gesture must not be read as
 * permission to destroy it.
 *
 * @param reason why no copy could be taken, worded by the vault. This sheet shows it and
 * decides nothing from it.
 * @param action how the sheet underneath names what is about to happen, so that going ahead
 * is labelled with the deletion itself rather than with the copy that is missing.
 */
class ProceedWithoutCopyModal(
    private val reason: UiText,
    private val action: StringResource,
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
        val modal = this@ProceedWithoutCopyModal

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

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { manager.dismiss(modal) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("no_copy_cancel"),
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
                        .testTag("no_copy_action"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                ) {
                    Text(
                        text = stringResource(action),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Puts the question about going on without a copy up while there is one, over the sheet that
 * asked for the action.
 *
 * The sheet is rendered by the modal manager, outside this tree, so the state is what keeps
 * the two in step: the view model publishes a refusal and this shows it; the view model
 * drops it — because an answer came, or because the action ended — and this takes down
 * whatever is left standing.
 */
@Composable
fun ProceedWithoutCopyHost(
    reason: UiText?,
    action: StringResource,
    onProceed: () -> Unit,
    onAbandon: () -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (reason != null) {
        val modal = remember(reason) {
            ProceedWithoutCopyModal(
                reason = reason,
                action = action,
                onProceed = onProceed,
                onAbandon = onAbandon,
            )
        }

        DisposableEffect(modal) {
            modalManager.show(modal)
            onDispose { modalManager.dismiss(modal) }
        }
    }
}
