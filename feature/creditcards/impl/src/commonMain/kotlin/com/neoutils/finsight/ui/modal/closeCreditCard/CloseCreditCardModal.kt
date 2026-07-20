package com.neoutils.finsight.ui.modal.closeCreditCard

import androidx.compose.foundation.layout.*
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
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.close_account_confirm
import com.neoutils.finsight.resources.close_credit_card_blocked
import com.neoutils.finsight.resources.close_credit_card_message
import com.neoutils.finsight.resources.close_credit_card_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Retiring a card that has movement — see `CloseAccountModal`; a card is its
 * `LIABILITY` account wearing a facade, and retires by the same rule.
 */
class CloseCreditCardModal(
    private val creditCard: CreditCard
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<CloseCreditCardViewModel> { parametersOf(creditCard) }
        val balance by viewModel.balance.collectAsState()
        val formatter = LocalCurrencyFormatter.current
        val blocked = balance != null && balance != 0.0


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(Res.string.close_credit_card_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (blocked) {
                    // A card balance is a debt, so it reads positive to the user.
                    stringResource(Res.string.close_credit_card_blocked, formatter.format(-(balance ?: 0.0)))
                } else {
                    stringResource(Res.string.close_credit_card_message)
                },
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.closeCreditCard()
                },
                enabled = balance == 0.0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(Res.string.close_account_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
