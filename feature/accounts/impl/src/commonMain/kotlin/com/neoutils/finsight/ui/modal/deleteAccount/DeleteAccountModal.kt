package com.neoutils.finsight.ui.modal.deleteAccount

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.close_account_confirm
import com.neoutils.finsight.resources.close_account_message
import com.neoutils.finsight.resources.close_account_message_with_balance
import com.neoutils.finsight.resources.close_account_title
import com.neoutils.finsight.resources.delete_account_confirm
import com.neoutils.finsight.resources.delete_account_message
import com.neoutils.finsight.resources.delete_account_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class DeleteAccountModal(
    private val account: Account
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<DeleteAccountViewModel> { parametersOf(account) }
        val outcome by viewModel.outcome.collectAsState()

        // The button names what will happen. An account with movement cannot be
        // removed without breaking the entries that reference it, so it is closed —
        // and calling that "excluir" would promise something the ledger never does.
        val willClose = outcome != null && outcome != CloseAccountUseCase.Outcome.DELETED

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(
                    if (willClose) Res.string.close_account_title else Res.string.delete_account_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    when (outcome) {
                        CloseAccountUseCase.Outcome.CLOSED_WITH_WRITE_OFF -> Res.string.close_account_message_with_balance
                        CloseAccountUseCase.Outcome.CLOSED -> Res.string.close_account_message
                        else -> Res.string.delete_account_message
                    }
                ),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.deleteAccount()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(
                        if (willClose) Res.string.close_account_confirm else Res.string.delete_account_confirm
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
