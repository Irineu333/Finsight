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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.OperationLabel
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.operation_card_balance_adjustment
import com.neoutils.finsight.resources.operation_card_invoice_adjustment
import com.neoutils.finsight.resources.operation_card_payment
import com.neoutils.finsight.resources.operation_card_transfer
import com.neoutils.finsight.ui.model.OperationUi
import com.neoutils.finsight.ui.theme.*
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource

@Composable
fun OperationCard(
    operation: OperationUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountDecoration: TextDecoration = TextDecoration.None,
) {
    val formatter = LocalCurrencyFormatter.current
    val color = operation.color()

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
                    color = color.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val categoryIcon = operation.categoryIcon
                        if (categoryIcon != null) {
                            Icon(
                                painter = categoryIcon(),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = operation.icon(),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (operation.isCardTarget) {
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
                    text = operation.displayTitle(paymentLabel, transferLabel, balanceAdjustLabel, invoiceAdjustLabel),
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
                text = when (operation.direction) {
                    Transaction.Type.ADJUSTMENT -> formatter.formatWithSign(operation.amount)
                    Transaction.Type.EXPENSE -> if (operation.label == OperationLabel.TRANSFER) {
                        "-${formatter.format(operation.amount)}"
                    } else {
                        formatter.format(operation.amount)
                    }
                    else -> formatter.format(operation.amount)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                textDecoration = amountDecoration,
            )
        }
    }
}

private fun OperationUi.displayTitle(
    paymentLabel: String,
    transferLabel: String,
    balanceAdjustLabel: String,
    invoiceAdjustLabel: String,
): String {
    val baseTitle = when {
        label == OperationLabel.PAYMENT -> paymentLabel
        label == OperationLabel.TRANSFER -> transferLabel
        label == OperationLabel.ADJUSTMENT && !isCardTarget -> balanceAdjustLabel
        label == OperationLabel.ADJUSTMENT && isCardTarget -> invoiceAdjustLabel
        else -> title
    }

    return installmentLabel?.let { "$baseTitle • $it" } ?: baseTitle
}

private fun OperationUi.icon() = when {
    label == OperationLabel.PAYMENT -> Icons.Default.Payment
    label == OperationLabel.TRANSFER -> Icons.Default.SwapHoriz
    else -> when (direction) {
        Transaction.Type.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
        Transaction.Type.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
        Transaction.Type.ADJUSTMENT -> Icons.Default.Tune
    }
}

private fun OperationUi.color(): Color = when {
    label == OperationLabel.PAYMENT -> InvoicePayment
    label == OperationLabel.TRANSFER -> Info
    direction == Transaction.Type.INCOME -> Income
    direction == Transaction.Type.EXPENSE -> Expense
    else -> Adjustment
}
