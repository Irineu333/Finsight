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
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_card_balance_adjustment
import com.neoutils.finsight.resources.transaction_card_invoice_adjustment
import com.neoutils.finsight.resources.transaction_card_payment
import com.neoutils.finsight.resources.transaction_card_transfer
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.ui.theme.*
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransactionCard(
    transaction: TransactionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountDecoration: TextDecoration = TextDecoration.None,
) {
    val formatter = LocalCurrencyFormatter.current
    val color = transaction.color()
    // The transaction keeps its own colour; only its category icon reads muted when
    // the category is archived — present in the history, out of circulation.
    val iconColor = if (transaction.categoryIcon != null && transaction.isCategoryArchived) {
        colorScheme.onSurfaceVariant
    } else {
        color
    }

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
                    color = iconColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val categoryIcon = transaction.categoryIcon
                        if (categoryIcon != null) {
                            Icon(
                                painter = categoryIcon(),
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = transaction.icon(),
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (transaction.isCardTarget) {
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

            val paymentLabel = stringResource(Res.string.transaction_card_payment)
            val transferLabel = stringResource(Res.string.transaction_card_transfer)
            val balanceAdjustLabel = stringResource(Res.string.transaction_card_balance_adjustment)
            val invoiceAdjustLabel = stringResource(Res.string.transaction_card_invoice_adjustment)

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.displayTitle(paymentLabel, transferLabel, balanceAdjustLabel, invoiceAdjustLabel),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dayMonthYear.format(transaction.date),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = when (transaction.direction) {
                    TransactionType.ADJUSTMENT -> formatter.formatWithSign(transaction.amount)
                    TransactionType.EXPENSE -> if (transaction.label == TransactionLabel.TRANSFER) {
                        "-${formatter.format(transaction.amount)}"
                    } else {
                        formatter.format(transaction.amount)
                    }
                    else -> formatter.format(transaction.amount)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                textDecoration = amountDecoration,
            )
        }
    }
}

private fun TransactionUi.displayTitle(
    paymentLabel: String,
    transferLabel: String,
    balanceAdjustLabel: String,
    invoiceAdjustLabel: String,
): String {
    val baseTitle = when {
        label == TransactionLabel.PAYMENT -> paymentLabel
        label == TransactionLabel.TRANSFER -> transferLabel
        label == TransactionLabel.ADJUSTMENT && !isCardTarget -> balanceAdjustLabel
        label == TransactionLabel.ADJUSTMENT && isCardTarget -> invoiceAdjustLabel
        else -> title
    }

    return installmentLabel?.let { "$baseTitle • $it" } ?: baseTitle
}

private fun TransactionUi.icon() = when {
    label == TransactionLabel.PAYMENT -> Icons.Default.Payment
    label == TransactionLabel.TRANSFER -> Icons.Default.SwapHoriz
    else -> when (direction) {
        TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
        TransactionType.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
        TransactionType.ADJUSTMENT -> Icons.Default.Tune
    }
}

private fun TransactionUi.color(): Color = when {
    label == TransactionLabel.PAYMENT -> InvoicePayment
    label == TransactionLabel.TRANSFER -> Info
    direction == TransactionType.INCOME -> Income
    direction == TransactionType.EXPENSE -> Expense
    else -> Adjustment
}
