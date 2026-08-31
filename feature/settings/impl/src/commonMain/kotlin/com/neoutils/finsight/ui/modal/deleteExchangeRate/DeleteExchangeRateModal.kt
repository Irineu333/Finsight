package com.neoutils.finsight.ui.modal.deleteExchangeRate

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.feature.backup.api.KeptCopyNotice
import com.neoutils.finsight.feature.backup.api.ProceedWithoutCopyHost
import com.neoutils.finsight.feature.backup.api.VaultOfferRow
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.delete_exchange_rate_confirm
import com.neoutils.finsight.resources.delete_exchange_rate_message
import com.neoutils.finsight.resources.delete_exchange_rate_message_reversible
import com.neoutils.finsight.resources.delete_exchange_rate_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Asking before one rate observation goes.
 *
 * It is the same sheet every other deletion of this app puts up — title, what is lost, a
 * full-width `error` button — because the removal it guards is the same kind of thing:
 * typed work, gone, with no other path back to it. The form that opens this stays
 * underneath and is dismissed with it once the write returns.
 *
 * The pair is in the title because the form stating it is covered by this sheet, and a
 * confirmation that cannot say *which* rate is not a confirmation.
 */
class DeleteExchangeRateModal(
    private val rate: ExchangeRate,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<DeleteExchangeRateViewModel> { parametersOf(rate) }
        val captureRefusal by viewModel.captureRefusal.collectAsStateWithLifecycle()

        // Over this sheet rather than in place of it: the observation being removed is
        // still stated above, and the question only adds that nothing is being kept back.
        ProceedWithoutCopyHost(
            reason = captureRefusal,
            action = Res.string.delete_exchange_rate_confirm,
            onProceed = viewModel::removeWithoutCopy,
            onAbandon = viewModel::abandonRemoval,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(
                    Res.string.delete_exchange_rate_title,
                    rate.currency,
                    rate.counterCurrency,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The sheet says what the app will actually do: with the copy kept first the
            // loss is not permanent, so the sentence saying it is goes rather than standing
            // beside one that contradicts it.
            Text(
                text = stringResource(
                    if (viewModel.keepsCopy) {
                        Res.string.delete_exchange_rate_message_reversible
                    } else {
                        Res.string.delete_exchange_rate_message
                    }
                ),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            if (viewModel.keepsCopy) {
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
                onClick = { viewModel.remove() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_exchange_rate_confirm"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
            ) {
                Text(
                    text = stringResource(Res.string.delete_exchange_rate_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
