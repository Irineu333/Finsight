@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finsight.feature.transactions.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.core.ui.extension.LocalCurrencyFormatter
import com.neoutils.finsight.core.ui.theme.*
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.utils.util.dayMonthYear
import com.neoutils.finsight.feature.transactions.model.OperationUi
import com.neoutils.finsight.feature.transactions.ui.resources.Res
import com.neoutils.finsight.feature.transactions.ui.resources.operation_card_balance_adjustment
import com.neoutils.finsight.feature.transactions.ui.resources.operation_card_invoice_adjustment
import com.neoutils.finsight.feature.transactions.ui.resources.operation_card_payment
import com.neoutils.finsight.feature.transactions.ui.resources.operation_card_transfer
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource

@Composable
fun OperationCard(
    operationUi: OperationUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountDecoration: TextDecoration = TextDecoration.None,
) {
    val formatter = LocalCurrencyFormatter.current
    val operation = operationUi.operation
    val displayType = operationUi.displayType
    val displayAmount = operationUi.displayAmount
    val displayTarget = operationUi.displayTarget
    val displayCategory = operationUi.displayCategory

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    color = operation.color(displayType).copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (displayCategory != null) {
                            Icon(
                                imageVector = AppIcon.fromKey(displayCategory.iconKey).icon,
                                contentDescription = null,
                                tint = operation.color(displayType),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = when {
                                    operation.kind == Operation.Kind.PAYMENT -> Icons.Default.Payment
                                    operation.kind == Operation.Kind.TRANSFER -> Icons.Default.SwapHoriz
                                    else -> when (displayType) {
                                        Transaction.Type.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                                        Transaction.Type.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                                        Transaction.Type.ADJUSTMENT -> Icons.Default.Tune
                                    }
                                },
                                contentDescription = null,
                                tint = operation.color(displayType),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (displayTarget.isCreditCard || operation.kind == Operation.Kind.PAYMENT) {
                    Surface(
                        color = colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(20.dp)
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

            val paymentLabel = stringResource(Res.string.operation_card_payment)
            val transferLabel = stringResource(Res.string.operation_card_transfer)
            val balanceAdjustLabel = stringResource(Res.string.operation_card_balance_adjustment)
            val invoiceAdjustLabel = stringResource(Res.string.operation_card_invoice_adjustment)

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getTitle(operationUi, paymentLabel, transferLabel, balanceAdjustLabel, invoiceAdjustLabel),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dayMonthYear.format(operation.date),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = when (displayType) {
                    Transaction.Type.ADJUSTMENT -> {
                        formatter.formatWithSign(displayAmount)
                    }

                    Transaction.Type.EXPENSE -> {
                        if (operation.kind == Operation.Kind.TRANSFER) {
                            "-${formatter.format(displayAmount)}"
                        } else {
                            formatter.format(displayAmount)
                        }
                    }

                    else -> {
                        formatter.format(displayAmount)
                    }
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = operation.color(displayType),
                textDecoration = amountDecoration,
            )
        }
    }
}

private fun getTitle(
    operationUi: OperationUi,
    paymentLabel: String,
    transferLabel: String,
    balanceAdjustLabel: String,
    invoiceAdjustLabel: String,
): String {
    val operation = operationUi.operation
    val displayType = operationUi.displayType
    val displayTarget = operationUi.displayTarget
    val baseTitle = when {
        operation.kind == Operation.Kind.PAYMENT -> paymentLabel
        operation.kind == Operation.Kind.TRANSFER -> transferLabel
        displayType == Transaction.Type.ADJUSTMENT && displayTarget.isAccount -> balanceAdjustLabel
        displayType == Transaction.Type.ADJUSTMENT && displayTarget.isCreditCard -> invoiceAdjustLabel
        else -> operationUi.displayLabel
    }

    val installment = operation.installment
    if (installment != null) {
        return "$baseTitle • ${installment.label}"
    }

    return baseTitle
}

private fun Operation.color(displayType: Transaction.Type) = when {
    kind == Operation.Kind.PAYMENT -> InvoicePayment
    kind == Operation.Kind.TRANSFER -> Info
    displayType == Transaction.Type.INCOME -> Income
    displayType == Transaction.Type.EXPENSE -> Expense
    else -> Adjustment
}
