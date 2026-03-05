@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
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
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.LocalNavigator
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.NavigationAction
import com.neoutils.finsight.ui.extension.toLabel
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewAdjustmentModal(
    private val operation: Operation
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val formatter = LocalCurrencyFormatter.current
        val manager = LocalModalManager.current
        val navigator = LocalNavigator.current
        val viewModel = koinViewModel<ViewAdjustmentViewModel> { parametersOf(operation) }
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
                AdjustmentIconBox(
                    showCreditCardBadge = uiState.transaction.target.isCreditCard
                )

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = stringResource(Res.string.view_adjustment_type_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Adjustment
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val balanceAdjust = stringResource(Res.string.view_adjustment_balance_adjust)
                    val invoiceAdjust = stringResource(Res.string.view_adjustment_invoice_adjust)
                    Text(
                        text = uiState.transaction.title ?: when (uiState.transaction.target) {
                            Transaction.Target.ACCOUNT -> balanceAdjust
                            Transaction.Target.CREDIT_CARD -> invoiceAdjust
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = stringResource(Res.string.view_adjustment_adjusted_value_label),
                value = formatter.formatWithSign(uiState.transaction.amount),
                valueColor = Adjustment
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_adjustment_date_label),
                value = dayMonthYear.format(uiState.transaction.date)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val creditCardLabel = stringResource(Res.string.view_adjustment_credit_card_label)
            val accountTypeLabel = stringResource(Res.string.view_adjustment_account_label)
            DetailRow(
                label = stringResource(Res.string.view_adjustment_type_row_label),
                value = when (uiState.transaction.target) {
                    Transaction.Target.ACCOUNT -> accountTypeLabel
                    Transaction.Target.CREDIT_CARD -> creditCardLabel
                }
            )

            uiState.transaction.account?.let { account ->
                DetailRow(
                    label = stringResource(Res.string.view_adjustment_account_label),
                    value = account.name,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    onClick = {
                        manager.dismissAll()
                        navigator.navigate(NavigationAction.Accounts(account.id))
                    }
                )
            }

            val deletedLabel = stringResource(Res.string.view_adjustment_deleted)
            uiState.transaction.creditCard?.let { creditCard ->
                DetailRow(
                    label = stringResource(Res.string.view_adjustment_card_label),
                    value = creditCard.name,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    onClick = {
                        manager.dismissAll()
                        navigator.navigate(NavigationAction.CreditCards(creditCard.id))
                    }
                )
            } ?: run {
                if (uiState.transaction.target == Transaction.Target.CREDIT_CARD) {
                    DetailRow(
                        label = stringResource(Res.string.view_adjustment_card_label),
                        value = deletedLabel,
                        valueColor = colorScheme.error,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    )
                }
            }

            uiState.transaction.invoice?.let { invoice ->
                val creditCardId = uiState.transaction.creditCard?.id
                DetailRow(
                    label = stringResource(Res.string.view_operation_invoice_label),
                    value = invoice.toLabel(),
                    valueColor = invoice.status.color,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    onClick = creditCardId?.let {
                        {
                            manager.dismissAll()
                            navigator.navigate(NavigationAction.InvoiceTransactions(it))
                        }
                    }
                )
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
                    text = stringResource(Res.string.view_adjustment_delete_label),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    private fun AdjustmentIconBox(
        showCreditCardBadge: Boolean
    ) {
        Box {
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Adjustment,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (showCreditCardBadge) {
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
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
}
