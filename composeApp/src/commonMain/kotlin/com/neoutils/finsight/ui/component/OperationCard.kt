@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.extension.toMoneyFormatWithSign
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.util.DateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats

private val formats = DateFormats()

@Composable
fun OperationCard(
    operation: Operation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountDecoration: TextDecoration = TextDecoration.None,
) = Card(
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
                color = operation.color().copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (operation.category != null) {
                        Icon(
                            painter = operation.category.icon(),
                            contentDescription = null,
                            tint = operation.color(),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                operation.kind == Operation.Kind.PAYMENT -> Icons.Default.Payment
                                operation.kind == Operation.Kind.TRANSFER -> Icons.Default.SwapHoriz
                                else -> when (operation.type) {
                                    Transaction.Type.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                                    Transaction.Type.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                                    Transaction.Type.ADJUSTMENT -> Icons.Default.Tune
                                }
                            },
                            contentDescription = null,
                            tint = operation.color(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (operation.target.isCreditCard || operation.kind == Operation.Kind.PAYMENT) {
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

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = getTitle(operation),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formats.dayMonthYear.format(operation.date),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = when (operation.type) {
                Transaction.Type.ADJUSTMENT -> {
                    operation.amount.toMoneyFormatWithSign()
                }

                Transaction.Type.EXPENSE -> {
                    if (operation.kind == Operation.Kind.TRANSFER) {
                        "-${operation.amount.toMoneyFormat()}"
                    } else {
                        operation.amount.toMoneyFormat()
                    }
                }

                else -> {
                    operation.amount.toMoneyFormat()
                }
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = operation.color(),
            textDecoration = amountDecoration,
        )
    }
}

private fun getTitle(
    operation: Operation,
): String {
    val baseTitle = when {
        operation.kind == Operation.Kind.PAYMENT -> operation.label
        operation.kind == Operation.Kind.TRANSFER -> "Transferência"
        operation.type == Transaction.Type.ADJUSTMENT && operation.target.isAccount -> "Ajuste de Saldo"
        operation.type == Transaction.Type.ADJUSTMENT && operation.target.isCreditCard -> "Ajuste de Fatura"
        else -> operation.label
    }

    val installment = operation.installment
    if (installment != null) {
        return "$baseTitle • ${installment.label}"
    }

    return baseTitle
}

private fun Operation.color() = when {
    kind == Operation.Kind.PAYMENT -> InvoicePayment
    kind == Operation.Kind.TRANSFER -> Info
    type == Transaction.Type.INCOME -> Income
    type == Transaction.Type.EXPENSE -> Expense
    else -> Adjustment
}
