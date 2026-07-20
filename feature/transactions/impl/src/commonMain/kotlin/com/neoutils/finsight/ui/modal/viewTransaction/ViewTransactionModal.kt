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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.toLabel
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.creditcards.api.InstallmentsRoute
import com.neoutils.finsight.feature.creditcards.api.InvoiceTransactionsRoute
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionModal
import com.neoutils.finsight.ui.model.TransactionPerspective
import com.neoutils.finsight.ui.theme.*
import com.neoutils.finsight.util.dayMonthYear
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewTransactionModal(
    private val transactionId: Long,
    private val perspective: TransactionPerspective? = null,
) : AdaptiveModal() {

    @Composable
    override fun DetailContent() {

        val formatter = LocalCurrencyFormatter.current
        val viewModel = koinViewModel<ViewTransactionViewModel> {
            parametersOf(transactionId, perspective)
        }

        val uiState by viewModel.uiState.collectAsState()

        val detailController = LocalDetailPaneController.current
        val recurringEntry = koinInject<RecurringEntry>()
        val navController = LocalNavController.current

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewTransactionEvent.Dismiss -> detailController.dismiss()
                    is ViewTransactionEvent.OpenRecurring -> detailController.show(
                        recurringEntry.viewRecurringModal(event.recurring.id)
                    )
                }
            }
        }

        when (val state = uiState) {
            ViewTransactionUiState.Loading -> DetailLoadingState()
            ViewTransactionUiState.Error -> DetailErrorState()
            is ViewTransactionUiState.Content -> ContentBody(
                uiState = state,
                formatter = formatter,
                detailController = detailController,
                navController = navController,
                viewModel = viewModel,
            )
        }
    }

    @Composable
    private fun ContentBody(
        uiState: ViewTransactionUiState.Content,
        formatter: com.neoutils.finsight.extension.CurrencyFormatter,
        detailController: com.neoutils.finsight.ui.component.DetailPaneController,
        navController: androidx.navigation.NavController,
        viewModel: ViewTransactionViewModel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Surface(
                        color = uiState.transactionColor().copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp),
                    ) {
                        uiState.category?.let { category ->
                            Icon(
                                painter = category.icon(),
                                contentDescription = null,
                                tint = uiState.transactionColor(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } ?: run {
                            Icon(
                                imageVector = when {
                                    uiState.label == TransactionLabel.PAYMENT -> Icons.Default.Payment
                                    uiState.label == TransactionLabel.TRANSFER -> Icons.Default.SwapHoriz
                                    uiState.direction == TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                                    uiState.direction == TransactionType.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                                    else -> Icons.Default.Tune
                                },
                                contentDescription = null,
                                tint = uiState.transactionColor(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        }
                    }

                    if (uiState.isCardTarget || uiState.label == TransactionLabel.PAYMENT) {
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
                        text = when (uiState.direction) {
                            TransactionType.INCOME -> stringResource(Res.string.view_transaction_type_income)
                            TransactionType.EXPENSE -> stringResource(Res.string.view_transaction_type_expense)
                            TransactionType.ADJUSTMENT -> stringResource(Res.string.view_transaction_type_adjustment)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = uiState.transactionColor()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (uiState.label) {
                            TransactionLabel.PAYMENT -> stringResource(Res.string.transaction_card_payment)
                            TransactionLabel.TRANSFER -> stringResource(Res.string.transaction_card_transfer)
                            else -> uiState.transaction.displayTitle
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = stringResource(Res.string.view_transaction_amount_label),
                value = formatter.format(uiState.amount),
                valueColor = uiState.transactionColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_transaction_date_label),
                value = dayMonthYear.format(uiState.date)
            )

            val originAccountLabel = stringResource(Res.string.view_transaction_origin_account)
            val originCreditCardLabel = stringResource(Res.string.view_transaction_origin_credit_card)
            if (uiState.direction.isExpense) {
                DetailRow(
                    label = stringResource(Res.string.view_transaction_origin_label),
                    value = if (uiState.isCardTarget) originCreditCardLabel else originAccountLabel,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.label == TransactionLabel.TRANSFER) {
                val sourceAccount = uiState.sourceAccount
                val destinationAccount = uiState.destinationAccount

                sourceAccount?.let { account ->
                    DetailRow(
                        label = stringResource(Res.string.view_transaction_source_account_label),
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            detailController.dismiss()
                            navController.navigate(AccountsRoute(account.id))
                        }
                    )
                }

                destinationAccount?.let { account ->
                    DetailRow(
                        label = stringResource(Res.string.view_transaction_destination_account_label),
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            detailController.dismiss()
                            navController.navigate(AccountsRoute(account.id))
                        }
                    )
                }
            }

            if (uiState.label != TransactionLabel.TRANSFER) {
                uiState.account?.let { account ->
                    DetailRow(
                        label = stringResource(Res.string.view_transaction_account_label),
                        value = account.name,
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            detailController.dismiss()
                            navController.navigate(AccountsRoute(account.id))
                        }
                    )
                }
            }

            val deletedLabel = stringResource(Res.string.view_transaction_deleted)
            uiState.creditCard?.let { creditCard ->
                DetailRow(
                    label = stringResource(Res.string.view_transaction_card_label),
                    value = creditCard.name,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        detailController.dismiss()
                        navController.navigate(
                            CreditCardsRoute(creditCard.id)
                        )
                    }
                )
            } ?: run {
                if (uiState.isCardTarget) {
                    DetailRow(
                        label = stringResource(Res.string.view_transaction_card_label),
                        value = deletedLabel,
                        valueColor = colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            uiState.invoice?.let { invoice ->
                val creditCardId = uiState.creditCard?.id
                DetailRow(
                    label = stringResource(Res.string.view_transaction_invoice_label),
                    value = invoice.toLabel(),
                    valueColor = invoice.status.color,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = creditCardId?.let {
                        {
                            detailController.dismiss()
                            navController.navigate(
                                InvoiceTransactionsRoute(it)
                            )
                        }
                    }
                )
            }

            uiState.transaction.installment?.let { installment ->
                DetailRow(
                    label = stringResource(Res.string.view_transaction_installment_label),
                    value = "${installment.label} de ${formatter.format(installment.instance.totalAmount)}",
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        detailController.dismiss()
                        navController.navigate(InstallmentsRoute)
                    }
                )
            }

            uiState.transaction.recurring?.let { recurring ->
                DetailRow(
                    label = stringResource(Res.string.view_transaction_recurring_label),
                    value = recurring.label,
                    valueColor = colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        viewModel.onAction(
                            ViewTransactionAction.OpenRecurring(recurring.instance)
                        )
                    }
                )
            }
        }
    }

    @Composable
    override fun DetailActions() {
        val viewModel = koinViewModel<ViewTransactionViewModel> {
            parametersOf(transactionId, perspective)
        }
        val uiState by viewModel.uiState.collectAsState()

        val content = uiState as? ViewTransactionUiState.Content ?: return

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            content.invoice?.let { invoice ->
                if (invoice.status.isEditable) {
                    EditAndDelete(content)
                } else {
                    Text(
                        text = stringResource(Res.string.view_transaction_closed_invoice_message),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } ?: run {
                EditAndDelete(content)
            }
        }
    }

    @Composable
    private fun EditAndDelete(
        uiState: ViewTransactionUiState.Content,
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        val manager = LocalModalManager.current

        OutlinedButton(
            onClick = {
                manager.show(DeleteTransactionModal(uiState.transaction))
            },
            modifier = if (uiState.isEditable) Modifier.weight(1f) else Modifier.fillMaxWidth(),
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
                text = stringResource(Res.string.view_transaction_delete),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (uiState.isEditable) {
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
                        text = stringResource(Res.string.view_transaction_edit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
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

    private fun ViewTransactionUiState.Content.transactionColor() = when {
        label == TransactionLabel.PAYMENT -> InvoicePayment
        label == TransactionLabel.TRANSFER -> Info
        direction == TransactionType.INCOME -> Income
        direction == TransactionType.EXPENSE -> Expense
        else -> Adjustment
    }
}
