@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.util.DateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi

class ViewOperationModal(
    private val operation: Operation
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<ViewOperationViewModel> { parametersOf(operation) }

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
                androidx.compose.foundation.layout.Box {
                    Surface(
                        color = uiState.operationColor().copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp),
                    ) {
                        uiState.transaction.category?.let { category ->
                            Icon(
                                painter = category.icon(),
                                contentDescription = null,
                                tint = uiState.operationColor(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } ?: run {
                            Icon(
                                imageVector = when {
                                    uiState.operation.kind == Operation.Kind.PAYMENT -> Icons.Default.Payment
                                    uiState.operation.kind == Operation.Kind.TRANSFER -> Icons.Default.SwapHoriz
                                    uiState.transaction.type == Transaction.Type.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                                    uiState.transaction.type == Transaction.Type.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                                    else -> Icons.Default.Tune
                                },
                                contentDescription = null,
                                tint = uiState.operationColor(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        }
                    }

                    if (uiState.transaction.target.isCreditCard || uiState.operation.kind == Operation.Kind.PAYMENT) {
                        Surface(
                            color = colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = when (uiState.transaction.type) {
                            Transaction.Type.INCOME -> "Receita"
                            Transaction.Type.EXPENSE -> "Despesa"
                            Transaction.Type.ADJUSTMENT -> "Ajuste"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = uiState.operationColor()
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
                valueColor = uiState.operationColor()
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
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.operation.kind == Operation.Kind.TRANSFER) {
                val sourceAccount = uiState.operation.transactions
                    .firstOrNull { it.type == Transaction.Type.EXPENSE && it.target == Transaction.Target.ACCOUNT }
                    ?.account
                val destinationAccount = uiState.operation.transactions
                    .firstOrNull { it.type == Transaction.Type.INCOME && it.target == Transaction.Target.ACCOUNT }
                    ?.account

                sourceAccount?.let { account ->
                    DetailRow(
                        label = "Conta origem",
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                destinationAccount?.let { account ->
                    DetailRow(
                        label = "Conta destino",
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (uiState.operation.kind != Operation.Kind.TRANSFER) {
                uiState.transaction.account?.let { account ->
                    DetailRow(
                        label = "Conta",
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            uiState.transaction.creditCard?.let { creditCard ->
                DetailRow(
                    label = "Cartão",
                    value = creditCard.name,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } ?: run {
                if (uiState.transaction.target == Transaction.Target.CREDIT_CARD) {
                    DetailRow(
                        label = "Cartão",
                        value = "Excluído",
                        valueColor = colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            uiState.transaction.invoice?.let { invoice ->
                DetailRow(
                    label = "Fatura",
                    value = invoice.label,
                    valueColor = invoice.status.color,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            uiState.operation.installment?.let { installment ->
                DetailRow(
                    label = "Parcela",
                    value = "${installment.label} de ${installment.totalLabel}",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            uiState.transaction.invoice?.let { invoice ->
                when (invoice.status) {
                    Invoice.Status.FUTURE, Invoice.Status.OPEN, Invoice.Status.RETROACTIVE -> {
                        EditAndDelete(uiState)
                    }

                    Invoice.Status.CLOSED, Invoice.Status.PAID -> {
                        Text(
                            text = "Esta transação pertence a uma fatura fechada e não pode ser editada ou excluída.",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } ?: run {
                EditAndDelete(uiState)
            }
        }
    }

    @Composable
    private fun EditAndDelete(
        uiState: ViewOperationUiState,
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        val manager = LocalModalManager.current

        OutlinedButton(
            onClick = {
                manager.show(DeleteTransactionModal(uiState.transaction))
            },
            modifier = when {
                uiState.transaction.type == Transaction.Type.ADJUSTMENT -> Modifier.fillMaxWidth()
                !uiState.operation.isEditable -> Modifier.fillMaxWidth()
                uiState.operation.installment != null -> Modifier.fillMaxWidth()
                uiState.transaction.target == Transaction.Target.CREDIT_CARD && uiState.transaction.creditCard == null -> Modifier.fillMaxWidth()
                else -> Modifier.weight(1f)
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

        when {
            uiState.transaction.type == Transaction.Type.ADJUSTMENT -> Unit
            !uiState.operation.isEditable -> Unit
            uiState.operation.installment != null -> Unit
            uiState.transaction.target == Transaction.Target.CREDIT_CARD && uiState.transaction.creditCard == null -> Unit

            else -> {
                OutlinedButton(
                    onClick = {
                        manager.show(
                            EditTransactionModal(uiState.transaction)
                        )
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

    private fun ViewOperationUiState.operationColor() = when {
        operation.kind == Operation.Kind.PAYMENT -> InvoicePayment
        operation.kind == Operation.Kind.TRANSFER -> Info
        transaction.type == Transaction.Type.INCOME -> Income
        transaction.type == Transaction.Type.EXPENSE -> Expense
        else -> Adjustment
    }
}
