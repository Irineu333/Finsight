@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.feature.transactions.modal.viewTransaction

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
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.extension.LocalCurrencyFormatter
import com.neoutils.finsight.feature.transactions.resources.*
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.feature.home.dispatcher.LocalNavigationDispatcher
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.component.ModalErrorContent
import com.neoutils.finsight.feature.home.dispatcher.NavigationDestination
import com.neoutils.finsight.feature.creditCards.extension.toLabel
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.feature.transactions.modal.editTransaction.EditTransactionModal
import com.neoutils.finsight.feature.recurring.modal.ViewRecurringModalEntry
import org.koin.compose.koinInject
import com.neoutils.finsight.core.ui.theme.*
import com.neoutils.finsight.core.utils.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi
import com.neoutils.finsight.feature.transactions.ui.resources.Res as TxUiRes
import com.neoutils.finsight.feature.transactions.ui.resources.operation_card_payment
import com.neoutils.finsight.feature.transactions.ui.resources.operation_card_transfer

class ViewOperationModal(
    private val operationId: Long,
    private val perspective: OperationPerspective,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<ViewOperationViewModel> {
            parametersOf(operationId, perspective)
        }

        val uiState by viewModel.uiState.collectAsState()

        val manager = LocalModalManager.current
        val viewRecurringEntry = koinInject<ViewRecurringModalEntry>()

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewOperationEvent.OpenRecurring -> {
                        manager.show(viewRecurringEntry.create(event.recurringId))
                    }
                }
            }
        }

        when (val state = uiState) {
            ViewOperationUiState.Loading -> LoadingContent()
            ViewOperationUiState.Error -> ErrorContent()
            is ViewOperationUiState.Content -> Content(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(96.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    @Composable
    private fun ErrorContent() {
        val manager = LocalModalManager.current
        ModalErrorContent(
            message = stringResource(Res.string.view_operation_unavailable),
            onClose = { manager.dismiss() },
        )
    }

    @Composable
    private fun Content(
        state: ViewOperationUiState.Content,
        onAction: (ViewOperationAction) -> Unit,
    ) {
        val formatter = LocalCurrencyFormatter.current
        val manager = LocalModalManager.current
        val navigationDispatcher = LocalNavigationDispatcher.current

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
                        color = state.operationColor().copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp),
                    ) {
                        state.category?.let { category ->
                            Icon(
                                imageVector = AppIcon.fromKey(category.iconKey).icon,
                                contentDescription = null,
                                tint = state.operationColor(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } ?: run {
                            Icon(
                                imageVector = when {
                                    state.operation.kind == Operation.Kind.PAYMENT -> Icons.Default.Payment
                                    state.operation.kind == Operation.Kind.TRANSFER -> Icons.Default.SwapHoriz
                                    state.transaction.type == Transaction.Type.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                                    state.transaction.type == Transaction.Type.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                                    else -> Icons.Default.Tune
                                },
                                contentDescription = null,
                                tint = state.operationColor(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        }
                    }

                    if (state.transaction.target.isCreditCard || state.operation.kind == Operation.Kind.PAYMENT) {
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
                        text = when (state.transaction.type) {
                            Transaction.Type.INCOME -> stringResource(Res.string.view_operation_type_income)
                            Transaction.Type.EXPENSE -> stringResource(Res.string.view_operation_type_expense)
                            Transaction.Type.ADJUSTMENT -> stringResource(Res.string.view_operation_type_adjustment)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = state.operationColor()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (state.operation.kind) {
                            Operation.Kind.PAYMENT -> stringResource(TxUiRes.string.operation_card_payment)
                            Operation.Kind.TRANSFER -> stringResource(TxUiRes.string.operation_card_transfer)
                            else -> state.operation.defaultLabel
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = stringResource(Res.string.view_operation_amount_label),
                value = formatter.format(state.transaction.amount),
                valueColor = state.operationColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_operation_date_label),
                value = dayMonthYear.format(state.transaction.date)
            )

            val originAccountLabel = stringResource(Res.string.view_operation_origin_account)
            val originCreditCardLabel = stringResource(Res.string.view_operation_origin_credit_card)
            if (state.transaction.type.isExpense) {
                DetailRow(
                    label = stringResource(Res.string.view_operation_origin_label),
                    value = when (state.transaction.target) {
                        Transaction.Target.ACCOUNT -> originAccountLabel
                        Transaction.Target.CREDIT_CARD -> originCreditCardLabel
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.operation.kind == Operation.Kind.TRANSFER) {
                val sourceAccount = state.sourceAccount
                val destinationAccount = state.destinationAccount

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

            if (state.operation.kind != Operation.Kind.TRANSFER) {
                state.account?.let { account ->
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
            state.creditCard?.let { creditCard ->
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
                if (state.transaction.target == Transaction.Target.CREDIT_CARD) {
                    DetailRow(
                        label = stringResource(Res.string.view_operation_card_label),
                        value = deletedLabel,
                        valueColor = colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            state.invoice?.let { invoice ->
                val creditCardId = invoice.creditCardId
                DetailRow(
                    label = stringResource(Res.string.view_operation_invoice_label),
                    value = invoice.toLabel(),
                    valueColor = Color(invoice.status.colorValue),
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

            state.operation.installment?.let { installment ->
                DetailRow(
                    label = stringResource(Res.string.view_operation_installment_label),
                    value = "${installment.label} de ${formatter.format(installment.totalAmount)}",
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        manager.dismissAll()
                        navigationDispatcher.dispatch(NavigationDestination.Installments)
                    }
                )
            }

            state.operation.recurring?.let { recurring ->
                DetailRow(
                    label = stringResource(Res.string.view_operation_recurring_label),
                    value = recurring.label,
                    valueColor = colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        onAction(ViewOperationAction.OpenRecurring(recurring.id))
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            state.invoice?.let { invoice ->
                when (invoice.status) {
                    Invoice.Status.FUTURE, Invoice.Status.OPEN, Invoice.Status.RETROACTIVE -> {
                        EditAndDelete(state)
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
                EditAndDelete(state)
            }
        }
    }

    @Composable
    private fun EditAndDelete(
        state: ViewOperationUiState.Content,
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        val manager = LocalModalManager.current

        OutlinedButton(
            onClick = {
                manager.show(DeleteTransactionModal(state.transaction))
            },
            modifier = when {
                state.transaction.type == Transaction.Type.ADJUSTMENT -> Modifier.fillMaxWidth()
                !state.operation.isEditable -> Modifier.fillMaxWidth()
                state.operation.installment != null -> Modifier.fillMaxWidth()
                state.transaction.target == Transaction.Target.CREDIT_CARD && state.creditCard == null -> Modifier.fillMaxWidth()
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
            state.transaction.type == Transaction.Type.ADJUSTMENT -> Unit
            !state.operation.isEditable -> Unit
            state.operation.installment != null -> Unit
            state.transaction.target == Transaction.Target.CREDIT_CARD && state.creditCard == null -> Unit

            else -> {
                OutlinedButton(
                    onClick = {
                        manager.show(EditTransactionModal(state.transaction))
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

    private fun ViewOperationUiState.Content.operationColor() = when {
        operation.kind == Operation.Kind.PAYMENT -> InvoicePayment
        operation.kind == Operation.Kind.TRANSFER -> Info
        transaction.type == Transaction.Type.INCOME -> Income
        transaction.type == Transaction.Type.EXPENSE -> Expense
        else -> Adjustment
    }
}
