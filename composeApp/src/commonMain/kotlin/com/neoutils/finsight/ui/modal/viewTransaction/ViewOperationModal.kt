@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.*
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
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.LocalNavigationDispatcher
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.extension.toLabel
import com.neoutils.finsight.ui.model.OperationPerspective
import com.neoutils.finsight.ui.model.OperationUi
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionModal
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.ui.theme.*
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi

class ViewOperationModal(
    private val operation: Operation,
    private val perspective: OperationPerspective? = null,
) : ModalBottomSheet() {

    constructor(operationUi: OperationUi) : this(
        operation = operationUi.operation,
        perspective = operationUi.perspective,
    )

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val formatter = LocalCurrencyFormatter.current
        val viewModel = koinViewModel<ViewOperationViewModel> {
            parametersOf(operation, perspective)
        }

        val uiState by viewModel.uiState.collectAsState()

        val manager = LocalModalManager.current
        val navigationDispatcher = LocalNavigationDispatcher.current

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewOperationEvent.OpenRecurring -> manager.show(ViewRecurringModal(event.recurring))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Surface(
                        color = uiState.operationColor().copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp),
                    ) {
                        uiState.transaction.category?.let { category ->
                            Icon(
                                imageVector = AppIcon.fromKey(category.iconKey).icon,
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
                            Transaction.Type.INCOME -> stringResource(Res.string.view_operation_type_income)
                            Transaction.Type.EXPENSE -> stringResource(Res.string.view_operation_type_expense)
                            Transaction.Type.ADJUSTMENT -> stringResource(Res.string.view_operation_type_adjustment)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = uiState.operationColor()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (uiState.operation.kind) {
                            Operation.Kind.PAYMENT -> stringResource(Res.string.operation_card_payment)
                            Operation.Kind.TRANSFER -> stringResource(Res.string.operation_card_transfer)
                            else -> uiState.operation.label
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = stringResource(Res.string.view_operation_amount_label),
                value = formatter.format(uiState.transaction.amount),
                valueColor = uiState.operationColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_operation_date_label),
                value = dayMonthYear.format(uiState.transaction.date)
            )

            val originAccountLabel = stringResource(Res.string.view_operation_origin_account)
            val originCreditCardLabel = stringResource(Res.string.view_operation_origin_credit_card)
            if (uiState.transaction.type.isExpense) {
                DetailRow(
                    label = stringResource(Res.string.view_operation_origin_label),
                    value = when (uiState.transaction.target) {
                        Transaction.Target.ACCOUNT -> originAccountLabel
                        Transaction.Target.CREDIT_CARD -> originCreditCardLabel
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
                        label = stringResource(Res.string.view_operation_source_account_label),
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            manager.dismissAll()
                            navigationDispatcher.dispatch(NavigationDestination.Accounts(account.id))
                        }
                    )
                }

                destinationAccount?.let { account ->
                    DetailRow(
                        label = stringResource(Res.string.view_operation_destination_account_label),
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            manager.dismissAll()
                            navigationDispatcher.dispatch(NavigationDestination.Accounts(account.id))
                        }
                    )
                }
            }

            if (uiState.operation.kind != Operation.Kind.TRANSFER) {
                uiState.transaction.account?.let { account ->
                    DetailRow(
                        label = stringResource(Res.string.view_operation_account_label),
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            manager.dismissAll()
                            navigationDispatcher.dispatch(NavigationDestination.Accounts(account.id))
                        }
                    )
                }
            }

            val deletedLabel = stringResource(Res.string.view_operation_deleted)
            uiState.transaction.creditCard?.let { creditCard ->
                DetailRow(
                    label = stringResource(Res.string.view_operation_card_label),
                    value = creditCard.name,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        manager.dismissAll()
                        navigationDispatcher.dispatch(
                            NavigationDestination.CreditCards(creditCard.id)
                        )
                    }
                )
            } ?: run {
                if (uiState.transaction.target == Transaction.Target.CREDIT_CARD) {
                    DetailRow(
                        label = stringResource(Res.string.view_operation_card_label),
                        value = deletedLabel,
                        valueColor = colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            uiState.transaction.invoice?.let { invoice ->
                val creditCardId = uiState.transaction.creditCard?.id
                DetailRow(
                    label = stringResource(Res.string.view_operation_invoice_label),
                    value = invoice.toLabel(),
                    valueColor = invoice.status.color,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = creditCardId?.let {
                        {
                            manager.dismissAll()
                            navigationDispatcher.dispatch(
                                NavigationDestination.InvoiceTransactions(it)
                            )
                        }
                    }
                )
            }

            uiState.operation.installment?.let { installment ->
                DetailRow(
                    label = stringResource(Res.string.view_operation_installment_label),
                    value = "${installment.label} de ${formatter.format(installment.instance.totalAmount)}",
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        manager.dismissAll()
                        navigationDispatcher.dispatch(NavigationDestination.Installments)
                    }
                )
            }

                uiState.operation.recurring?.let { recurring ->
                    DetailRow(
                        label = stringResource(Res.string.view_operation_recurring_label),
                        value = recurring.label,
                        valueColor = colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            viewModel.onAction(
                                ViewOperationAction.OpenRecurring(recurring.instance)
                            )
                        }
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
                            text = stringResource(Res.string.view_operation_closed_invoice_message),
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
                text = stringResource(Res.string.view_operation_delete),
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
                        manager.show(EditTransactionModal(uiState.transaction))
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
                        text = stringResource(Res.string.view_operation_edit),
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
        valueColor: Color = colorScheme.onSurface,
        onClick: (() -> Unit)? = null,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ) {
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor
                )
            }
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
