package com.neoutils.finsight.ui.modal.clearAgentActivity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_activity_clear_confirm
import com.neoutils.finsight.resources.mcp_activity_clear_message
import com.neoutils.finsight.resources.mcp_activity_clear_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource

/**
 * Emptying the agent log, and **saying what it does not take with it**.
 *
 * The confirmation exists because the log is the only place authorship of a write ever appears, and
 * discarding it cannot be undone. The sentence it shows is the other half: no posting is touched —
 * the log is the trace of what was done, and the ledger is where what was done lives.
 */
class ClearAgentActivityModal(
    private val onConfirm: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val modal = this@ClearAgentActivityModal

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.mcp_activity_clear_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.mcp_activity_clear_message),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onConfirm()
                    modalManager.dismiss(modal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_activity_clear_confirm"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
            ) {
                Text(
                    text = stringResource(Res.string.mcp_activity_clear_confirm),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
