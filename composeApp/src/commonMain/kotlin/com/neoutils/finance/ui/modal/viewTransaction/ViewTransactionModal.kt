@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class, ExperimentalUuidApi::class)

package com.neoutils.finance.ui.modal.viewTransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.icons.CategoryLazyIcon
import com.neoutils.finance.util.CategoryIcon
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finance.ui.modal.editInvoicePayment.EditInvoicePaymentModal
import com.neoutils.finance.ui.modal.editTransaction.EditTransactionModal
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.InvoicePayment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.Info
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi

class ViewTransactionModal(
    private val transaction: Transaction
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current

        val viewModel = koinViewModel<ViewTransactionViewModel>(key = key) { parametersOf(transaction) }

        val uiState by viewModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = when (transaction.type) {
                        Transaction.Type.INCOME -> Income.copy(alpha = 0.2f)
                        Transaction.Type.EXPENSE -> Expense.copy(alpha = 0.2f)
                        Transaction.Type.ADJUSTMENT -> Adjustment.copy(alpha = 0.2f)
                        Transaction.Type.INVOICE_PAYMENT,
                        Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp),
                ) {

                    uiState.transaction.category?.let { category ->
                        Icon(
                            painter = category.icon(),
                            contentDescription = null,
                            tint = when (transaction.type) {
                                Transaction.Type.INCOME -> Income
                                Transaction.Type.EXPENSE -> Expense
                                Transaction.Type.ADJUSTMENT -> Adjustment
                                Transaction.Type.INVOICE_PAYMENT,
                                Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } ?: run {
                        Icon(
                            imageVector = when (transaction.type) {
                                Transaction.Type.INCOME -> Icons.Default.ShoppingCart
                                Transaction.Type.EXPENSE -> Icons.Default.Receipt
                                Transaction.Type.ADJUSTMENT -> Icons.Default.Edit
                                Transaction.Type.INVOICE_PAYMENT,
                                Transaction.Type.ADVANCE_PAYMENT -> Icons.Default.Receipt
                            },
                            contentDescription = null,
                            tint = when (transaction.type) {
                                Transaction.Type.INCOME -> Income
                                Transaction.Type.EXPENSE -> Expense
                                Transaction.Type.ADJUSTMENT -> Adjustment
                                Transaction.Type.INVOICE_PAYMENT,
                                Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = when (uiState.transaction.type) {
                            Transaction.Type.INCOME -> "Receita"
                            Transaction.Type.EXPENSE -> "Despesa"
                            Transaction.Type.ADJUSTMENT -> "Ajuste"
                            Transaction.Type.INVOICE_PAYMENT -> "Pagamento de Fatura"
                            Transaction.Type.ADVANCE_PAYMENT -> "Antecipação de Fatura"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (uiState.transaction.type) {
                            Transaction.Type.INCOME -> Income
                            Transaction.Type.EXPENSE -> Expense
                            Transaction.Type.ADJUSTMENT -> Adjustment
                            Transaction.Type.INVOICE_PAYMENT,
                            Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = "Valor",
                value = uiState.transaction.amount.toMoneyFormat(),
                valueColor = when (uiState.transaction.type) {
                    Transaction.Type.INCOME -> Income
                    Transaction.Type.EXPENSE -> Expense
                    Transaction.Type.ADJUSTMENT -> Adjustment
                    Transaction.Type.INVOICE_PAYMENT,
                    Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = "Data",
                value = formats.dayMonthYear.format(uiState.transaction.date)
            )

            if (uiState.transaction.type.isExpense) {
                DetailRow(
                    label = "Origem",
                    value = when (uiState.transaction.target) {
                        Transaction.Target.ACCOUNT -> "Conta"
                        Transaction.Target.CREDIT_CARD -> "Cartão de Crédito"
                        else -> error("Invalid type")
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            uiState.transaction.creditCard?.let { creditCard ->
                DetailRow(
                    label = "Cartão",
                    value = creditCard.name,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } ?: run {
                // Se target é cartão de crédito mas creditCard é null, cartão foi excluído
                if (uiState.transaction.target == Transaction.Target.CREDIT_CARD) {
                    DetailRow(
                        label = "Cartão",
                        value = "(Excluído)",
                        valueColor = colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Botões só aparecem se a transação não pertence a uma fatura fechada/paga
            val canModify = uiState.transaction.invoice?.let { invoice ->
                invoice.status == Invoice.Status.OPEN
            } ?: true

            if (canModify) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            manager.show(DeleteTransactionModal(uiState.transaction))
                        },
                        modifier = if (uiState.transaction.type == Transaction.Type.ADJUSTMENT) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.weight(1f)
                        },
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
                            text = "Excluir",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (uiState.transaction.type != Transaction.Type.ADJUSTMENT) {
                        OutlinedButton(
                            onClick = {
                                val modal = when (uiState.transaction.type) {
                                    Transaction.Type.INVOICE_PAYMENT -> EditInvoicePaymentModal(uiState.transaction)
                                    else -> EditTransactionModal(uiState.transaction)
                                }
                                manager.show(modal)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Info,
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = Info,
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Editar",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Fatura fechada/paga - exibir mensagem informativa
                Text(
                    text = "Esta transação pertence a uma fatura ${uiState.transaction.invoice?.status?.label?.lowercase() ?: "fechada"} e não pode ser modificada.",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
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
