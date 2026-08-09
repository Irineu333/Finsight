package com.neoutils.finsight.ui.modal.deleteCurrency

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_delete_confirm_action
import com.neoutils.finsight.resources.currencies_delete_confirm_message
import com.neoutils.finsight.resources.currencies_delete_confirm_message_rates
import com.neoutils.finsight.resources.currencies_delete_confirm_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Deleting a currency, and **saying what it takes with it**.
 *
 * A rate observation never blocks the deletion — it is removed in the same write — so the
 * number has to be stated here rather than destroyed quietly. It arrives already counted
 * from the screen that opened this: the count and the refusal are one answer, read once.
 */
class DeleteCurrencyModal(
    private val code: String,
    private val label: String,
    private val ratesToRemove: Int,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<DeleteCurrencyViewModel> { parametersOf(code) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.currencies_delete_confirm_title, label),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (ratesToRemove > 0) {
                    stringResource(
                        Res.string.currencies_delete_confirm_message_rates,
                        ratesToRemove,
                    )
                } else {
                    stringResource(Res.string.currencies_delete_confirm_message)
                },
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.delete() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
            ) {
                Text(
                    text = stringResource(Res.string.currencies_delete_confirm_action),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
