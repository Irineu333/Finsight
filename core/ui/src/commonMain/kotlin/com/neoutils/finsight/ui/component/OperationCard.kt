@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finsight.ui.component

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
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.operation_card_balance_adjustment
import com.neoutils.finsight.resources.operation_card_invoice_adjustment
import com.neoutils.finsight.resources.operation_card_payment
import com.neoutils.finsight.resources.operation_card_transfer
import com.neoutils.finsight.ui.theme.*
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource

@Composable
fun OperationCard(
    operation: Operation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountDecoration: TextDecoration = TextDecoration.None,
    displayType: Transaction.Type = operation.type,
    displayAmount: Double = operation.amount,
    displayTarget: Transaction.Target = operation.target,
    displayCategory: Category? = operation.category ?: operation.primaryTransaction.category,
) {
    val formatter = LocalCurrencyFormatter.current

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
                                painter = displayCategory.icon(),
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
                    text = getTitle(operation, paymentLabel, transferLabel, balanceAdjustLabel, invoiceAdjustLabel),
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
    operation: Operation,
    paymentLabel: String,
    transferLabel: String,
    balanceAdjustLabel: String,
    invoiceAdjustLabel: String,
): String {
    val baseTitle = when {
        operation.kind == Operation.Kind.PAYMENT -> paymentLabel
        operation.kind == Operation.Kind.TRANSFER -> transferLabel
        operation.type == Transaction.Type.ADJUSTMENT && operation.target.isAccount -> balanceAdjustLabel
        operation.type == Transaction.Type.ADJUSTMENT && operation.target.isCreditCard -> invoiceAdjustLabel
        else -> operation.label
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
