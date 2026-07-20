package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.error_modal_dismiss
import com.neoutils.finsight.resources.error_modal_title
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

/**
 * Why an action was refused, shown over the modal that tried it.
 *
 * The refusing modal stays open underneath on purpose: the reasons are things the
 * user can act on — a balance to resolve, a category still in use — so dismissing
 * this returns them to the action rather than to nowhere.
 */
class ErrorModal(
    private val message: UiText,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val manager = LocalModalManager.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = colorScheme.error,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.error_modal_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringUiText(message),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { manager.dismiss(this@ErrorModal) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.error_modal_dismiss),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
