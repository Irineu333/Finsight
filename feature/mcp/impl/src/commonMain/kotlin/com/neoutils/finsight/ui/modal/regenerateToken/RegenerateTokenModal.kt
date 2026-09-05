package com.neoutils.finsight.ui.modal.regenerateToken

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
import com.neoutils.finsight.resources.mcp_token_regenerate_confirm
import com.neoutils.finsight.resources.mcp_token_regenerate_message
import com.neoutils.finsight.resources.mcp_token_regenerate_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource

/**
 * Minting a new token, and **saying what stops working when it is minted**.
 *
 * Regenerating is not a variation of revealing or copying, though it sits beside them: those two
 * leave everything as it was, and this one invalidates the token every configured client holds and
 * rebinds the socket, so whoever is connected is disconnected. The confirmation is what keeps a
 * destructive action from being one tap away from two harmless ones — and the sentence it shows is
 * the part the user cannot infer from the icon: every client has to be pointed at the new token.
 */
class RegenerateTokenModal(
    private val onConfirm: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val modal = this@RegenerateTokenModal

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.mcp_token_regenerate_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.mcp_token_regenerate_message),
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
                    .testTag("mcp_token_regenerate_confirm"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
            ) {
                Text(
                    text = stringResource(Res.string.mcp_token_regenerate_confirm),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
