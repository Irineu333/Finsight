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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_delete_confirm_action
import com.neoutils.finsight.resources.currencies_delete_confirm_message
import com.neoutils.finsight.resources.currencies_delete_confirm_message_rates
import com.neoutils.finsight.resources.currencies_delete_confirm_message_rates_reversible
import com.neoutils.finsight.resources.currencies_delete_confirm_message_reversible
import com.neoutils.finsight.resources.currencies_delete_confirm_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.backup.api.KeptCopyNotice
import com.neoutils.finsight.feature.backup.api.ProceedWithoutCopyHost
import com.neoutils.finsight.feature.backup.api.VaultOfferRow
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
        val captureRefusal by viewModel.captureRefusal.collectAsStateWithLifecycle()
        val keepsCopy by viewModel.keepsCopy.collectAsStateWithLifecycle()

        // Over this sheet rather than in place of it: what is being deleted is still stated
        // above, and the question only adds that nothing is being kept back.
        ProceedWithoutCopyHost(
            reason = captureRefusal,
            action = Res.string.currencies_delete_confirm_action,
            onProceed = viewModel::deleteWithoutCopy,
            onAbandon = viewModel::abandonDeletion,
        )

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

            // The sheet says what the app will actually do: with the copy kept first the
            // loss is not permanent, so the sentence saying it is goes rather than standing
            // beside one that contradicts it.
            Text(
                text = if (ratesToRemove > 0) {
                    stringResource(
                        if (keepsCopy) {
                            Res.string.currencies_delete_confirm_message_rates_reversible
                        } else {
                            Res.string.currencies_delete_confirm_message_rates
                        },
                        ratesToRemove,
                    )
                } else {
                    stringResource(
                        if (keepsCopy) {
                            Res.string.currencies_delete_confirm_message_reversible
                        } else {
                            Res.string.currencies_delete_confirm_message
                        }
                    )
                },
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            if (keepsCopy) {
                Spacer(modifier = Modifier.height(8.dp))

                KeptCopyNotice()
            }

            // Inside the confirmation and above its button, where the risk it covers is
            // stated. It renders nothing, spacing included, where there is nothing left to
            // offer.
            VaultOfferRow(
                state = viewModel.offer,
                modifier = Modifier.padding(top = 16.dp),
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
