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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.extension.LocalCurrencyFormatter
import com.neoutils.finsight.feature.transactions.resources.*
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.feature.home.dispatcher.LocalNavigationDispatcher
import com.neoutils.finsight.feature.home.dispatcher.NavigationDispatcher
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
            Header(state)

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = stringResource(Res.string.view_operation_amount_label),
                value = formatter.format(state.transaction.amount),
                valueColor = state.color()
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_operation_date_label),
                value = dayMonthYear.format(state.transaction.date)
            )

            KindSpecificRows(
                state = state,
                manager = manager,
                navigationDispatcher = navigationDispatcher,
            )

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

            Actions(state)
        }
    }

    @Composable
    private fun Header(state: ViewOperationUiState.Content) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    color = state.color().copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp),
                ) {
                    val tint = state.color()
                    state.category?.let { category ->
                        Icon(
                            imageVector = AppIcon.fromKey(category.iconKey).icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } ?: run {
                        Icon(
                            imageVector = state.fallbackIcon(),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }

                if (state.showsCardBadge()) {
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
                    text = state.typeLabel(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = state.color()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = state.titleLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface
                )
            }
        }
    }

    @Composable
    private fun KindSpecificRows(
        state: ViewOperationUiState.Content,
        manager: ModalManager,
        navigationDispatcher: NavigationDispatcher,
    ) {
        when (state) {
            is ViewOperationUiState.Content.Single -> SingleRows(state, manager, navigationDispatcher)
            is ViewOperationUiState.Content.Transfer -> TransferRows(state, manager, navigationDispatcher)
            is ViewOperationUiState.Content.Payment -> PaymentRows(state, manager, navigationDispatcher)
        }
    }

    @Composable
    private fun SingleRows(
        state: ViewOperationUiState.Content.Single,
        manager: ModalManager,
        navigationDispatcher: NavigationDispatcher,
    ) {
        val originAccountLabel = stringResource(Res.string.view_operation_origin_account)
        val originCreditCardLabel = stringResource(Res.string.view_operation_origin_credit_card)
        val deletedLabel = stringResource(Res.string.view_operation_deleted)

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

        if (state.transaction.target == Transaction.Target.CREDIT_CARD) {
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
            } ?: DetailRow(
                label = stringResource(Res.string.view_operation_card_label),
                value = deletedLabel,
                valueColor = colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        state.invoice?.let { invoice ->
            InvoiceRow(invoice, manager, navigationDispatcher)
        }
    }

    @Composable
    private fun TransferRows(
        state: ViewOperationUiState.Content.Transfer,
        manager: ModalManager,
        navigationDispatcher: NavigationDispatcher,
    ) {
        state.sourceAccount?.let { account ->
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

        state.destinationAccount?.let { account ->
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

    @Composable
    private fun PaymentRows(
        state: ViewOperationUiState.Content.Payment,
        manager: ModalManager,
        navigationDispatcher: NavigationDispatcher,
    ) {
        val deletedLabel = stringResource(Res.string.view_operation_deleted)

        state.sourceAccount?.let { account ->
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
        } ?: DetailRow(
            label = stringResource(Res.string.view_operation_card_label),
            value = deletedLabel,
            valueColor = colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )

        state.invoice?.let { invoice ->
            InvoiceRow(invoice, manager, navigationDispatcher)
        }
    }

    @Composable
    private fun InvoiceRow(
        invoice: Invoice,
        manager: ModalManager,
        navigationDispatcher: NavigationDispatcher,
    ) {
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

    @Composable
    private fun Actions(state: ViewOperationUiState.Content) {
        when (state) {
            is ViewOperationUiState.Content.Single -> {
                val invoice = state.invoice
                if (invoice != null && !invoice.status.isEditable) {
                    Text(
                        text = stringResource(Res.string.view_operation_closed_invoice_message),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    SingleActions(state)
                }
            }

            is ViewOperationUiState.Content.Transfer,
            is ViewOperationUiState.Content.Payment -> {
                DeleteOnly(state)
            }
        }
    }

    @Composable
    private fun SingleActions(state: ViewOperationUiState.Content.Single) {
        val manager = LocalModalManager.current
        val canEdit = state.transaction.type != Transaction.Type.ADJUSTMENT &&
                state.operation.installment == null &&
                !(state.transaction.target == Transaction.Target.CREDIT_CARD && state.creditCard == null)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DeleteButton(
                state = state,
                modifier = if (canEdit) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )

            if (canEdit) {
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
    private fun DeleteOnly(state: ViewOperationUiState.Content) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DeleteButton(
                state = state,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun DeleteButton(
        state: ViewOperationUiState.Content,
        modifier: Modifier,
    ) {
        val manager = LocalModalManager.current
        OutlinedButton(
            onClick = { manager.show(DeleteTransactionModal(state.transaction)) },
            modifier = modifier,
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

    private fun ViewOperationUiState.Content.color(): Color = when (this) {
        is ViewOperationUiState.Content.Transfer -> Info
        is ViewOperationUiState.Content.Payment -> InvoicePayment
        is ViewOperationUiState.Content.Single -> when (transaction.type) {
            Transaction.Type.INCOME -> Income
            Transaction.Type.EXPENSE -> Expense
            Transaction.Type.ADJUSTMENT -> Adjustment
        }
    }

    private fun ViewOperationUiState.Content.fallbackIcon(): ImageVector = when (this) {
        is ViewOperationUiState.Content.Payment -> Icons.Default.Payment
        is ViewOperationUiState.Content.Transfer -> Icons.Default.SwapHoriz
        is ViewOperationUiState.Content.Single -> when (transaction.type) {
            Transaction.Type.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
            Transaction.Type.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
            Transaction.Type.ADJUSTMENT -> Icons.Default.Tune
        }
    }

    private fun ViewOperationUiState.Content.showsCardBadge(): Boolean = when (this) {
        is ViewOperationUiState.Content.Payment -> true
        is ViewOperationUiState.Content.Single -> transaction.target.isCreditCard
        is ViewOperationUiState.Content.Transfer -> false
    }

    @Composable
    private fun ViewOperationUiState.Content.typeLabel(): String = when (this) {
        is ViewOperationUiState.Content.Payment -> stringResource(Res.string.view_operation_type_expense)
        is ViewOperationUiState.Content.Single,
        is ViewOperationUiState.Content.Transfer -> when (transaction.type) {
            Transaction.Type.INCOME -> stringResource(Res.string.view_operation_type_income)
            Transaction.Type.EXPENSE -> stringResource(Res.string.view_operation_type_expense)
            Transaction.Type.ADJUSTMENT -> stringResource(Res.string.view_operation_type_adjustment)
        }
    }

    @Composable
    private fun ViewOperationUiState.Content.titleLabel(): String = when (this) {
        is ViewOperationUiState.Content.Payment -> stringResource(TxUiRes.string.operation_card_payment)
        is ViewOperationUiState.Content.Transfer -> stringResource(TxUiRes.string.operation_card_transfer)
        is ViewOperationUiState.Content.Single -> operation.defaultLabel
    }
}
