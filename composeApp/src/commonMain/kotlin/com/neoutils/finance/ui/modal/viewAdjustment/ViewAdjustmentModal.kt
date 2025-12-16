@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.ui.modal.viewAdjustment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toMoneyFormatWithSign
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewAdjustmentModal(
    private val transaction: Transaction
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val manager = LocalModalManager.current
        val viewModel = koinViewModel<ViewAdjustmentViewModel>(key = key) { parametersOf(transaction) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdjustmentIconBox()

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (uiState.transaction.target.isCreditCard) "Ajuste de Fatura" else "Ajuste de Saldo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Adjustment
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = uiState.transaction.title ?: "Ajuste de saldo",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = "Valor Ajustado",
                value = uiState.transaction.amount.toMoneyFormatWithSign(),
                valueColor = Adjustment
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = "Data",
                value = formats.dayMonthYear.format(uiState.transaction.date)
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = "Tipo",
                value = when (uiState.transaction.target) {
                    Transaction.Target.ACCOUNT -> "Conta"
                    Transaction.Target.CREDIT_CARD -> "Cartão de Crédito"
                    Transaction.Target.INVOICE_PAYMENT -> "Pagamento de Fatura"
                }
            )

            uiState.transaction.creditCard?.let { creditCard ->
                DetailRow(
                    label = "Cartão",
                    value = creditCard.name,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            } ?: run {
                if (uiState.transaction.target == Transaction.Target.CREDIT_CARD) {
                    DetailRow(
                        label = "Cartão",
                        value = "(Excluído)",
                        valueColor = colorScheme.error,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            OutlinedButton(
                onClick = {
                    manager.show(DeleteTransactionModal(uiState.transaction))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.error,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = colorScheme.error,
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Excluir Ajuste",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    private fun AdjustmentIconBox() {
        Surface(
            color = Adjustment.copy(alpha = 0.2f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(64.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Adjustment,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        modifier: Modifier = Modifier,
        valueColor: Color = colorScheme.onSurface
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}
