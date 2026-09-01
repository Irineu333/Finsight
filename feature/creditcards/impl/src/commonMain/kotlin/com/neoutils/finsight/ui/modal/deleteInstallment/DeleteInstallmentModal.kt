package com.neoutils.finsight.ui.modal.deleteInstallment

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
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.delete_installment_confirm
import com.neoutils.finsight.resources.delete_installment_message
import com.neoutils.finsight.resources.delete_installment_message_reversible
import com.neoutils.finsight.resources.delete_installment_title
import com.neoutils.finsight.feature.backup.api.KeptCopyNotice
import com.neoutils.finsight.feature.backup.api.ProceedWithoutCopyHost
import com.neoutils.finsight.feature.backup.api.VaultOfferRow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class DeleteInstallmentModal(
    private val installment: Installment,
    private val transactions: List<Transaction>,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<DeleteInstallmentViewModel> {
            parametersOf(installment, transactions)
        }
        val captureRefusal by viewModel.captureRefusal.collectAsStateWithLifecycle()
        val keepsCopy by viewModel.keepsCopy.collectAsStateWithLifecycle()

        // Over this sheet rather than in place of it: how many instalments are going is
        // still stated above, and the question only adds that nothing is being kept back.
        ProceedWithoutCopyHost(
            reason = captureRefusal,
            action = Res.string.delete_installment_confirm,
            onProceed = viewModel::deleteWithoutCopy,
            onAbandon = viewModel::abandonDeletion,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(Res.string.delete_installment_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The sheet says what the app will actually do: with the copy kept first the
            // loss is not permanent, so the sentence saying it is goes rather than standing
            // beside one that contradicts it.
            Text(
                text = stringResource(
                    if (keepsCopy) {
                        Res.string.delete_installment_message_reversible
                    } else {
                        Res.string.delete_installment_message
                    },
                    transactions.size,
                ),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            if (keepsCopy) {
                Spacer(modifier = Modifier.height(8.dp))

                KeptCopyNotice()
            }

            // Inside the confirmation and above its button, where the risk it covers is
            // stated: the sheet says twelve entries are going, and this is the one thing
            // that can still be changed about that. It renders nothing, spacing included,
            // where there is nothing left to offer.
            VaultOfferRow(
                state = viewModel.offer,
                modifier = Modifier.padding(top = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.deleteInstallment() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_installment_confirm"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.delete_installment_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
