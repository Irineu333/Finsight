package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_summary_card_advance_payments
import com.neoutils.finsight.resources.invoice_summary_card_adjustments
import com.neoutils.finsight.resources.invoice_summary_card_edit_invoice
import com.neoutils.finsight.resources.invoice_summary_card_expenses
import com.neoutils.finsight.resources.invoice_summary_card_invoice
import kotlin.math.absoluteValue
import org.jetbrains.compose.resources.stringResource

@Composable
fun InvoiceSummaryCard(
    overview: TransactionsUiState.InvoiceOverview,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = overview.creditCardName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )

                Surface(
                    color = overview.invoiceStatus.color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = overview.invoiceStatus.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = overview.invoiceStatus.color,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            AnimatedContent(
                targetState = overview,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { currentOverview ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InvoiceSummaryRow(
                        label = stringResource(Res.string.invoice_summary_card_expenses),
                        amount = currentOverview.expense,
                        color = Expense,
                        signDisplay = InvoiceSignDisplay.ALWAYS_NEGATIVE
                    )

                    InvoiceSummaryRow(
                        label = stringResource(Res.string.invoice_summary_card_advance_payments),
                        amount = currentOverview.advancePayment,
                        color = InvoicePayment,
                        signDisplay = InvoiceSignDisplay.ALWAYS_POSITIVE
                    )

                    if (currentOverview.mustShowAdjustment) {
                        InvoiceSummaryRow(
                            label = stringResource(Res.string.invoice_summary_card_adjustments),
                            amount = currentOverview.adjustment,
                            color = Adjustment,
                            signDisplay = InvoiceSignDisplay.SHOW_ALWAYS
                        )
                    }

                    HorizontalDivider()

                    InvoiceSummaryRow(
                        label = stringResource(Res.string.invoice_summary_card_invoice),
                        amount = currentOverview.total,
                        color = colorScheme.onSurface,
                        signDisplay = InvoiceSignDisplay.SHOW_ONLY_NEGATIVE,
                        isTotal = true,
                        onEditClick = onEditClick
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceSummaryRow(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    signDisplay: InvoiceSignDisplay = InvoiceSignDisplay.SHOW_ONLY_NEGATIVE,
    isTotal: Boolean = false,
    onEditClick: (() -> Unit)? = null
) {
    val formatter = LocalCurrencyFormatter.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isTotal) 18.sp else 16.sp,
            fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isTotal) colorScheme.onSurface else TextLight1
        )

        val formattedAmount = when (signDisplay) {
            InvoiceSignDisplay.ALWAYS_POSITIVE -> {
                "+${formatter.format(amount.absoluteValue)}"
            }

            InvoiceSignDisplay.ALWAYS_NEGATIVE -> {
                "-${formatter.format(amount.absoluteValue)}"
            }

            InvoiceSignDisplay.SHOW_ALWAYS -> {
                when {
                    amount > 0 -> "+${formatter.format(amount)}"
                    amount < 0 -> formatter.format(amount)
                    else -> formatter.format(amount)
                }
            }

            InvoiceSignDisplay.SHOW_ONLY_NEGATIVE -> {
                if (amount < 0) formatter.format(amount) else formatter.format(amount)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (onEditClick != null) {
                        Modifier.clickable { onEditClick() }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (onEditClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ModeEdit,
                    contentDescription = stringResource(Res.string.invoice_summary_card_edit_invoice),
                    tint = color.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = formattedAmount,
                fontSize = if (isTotal) 20.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private enum class InvoiceSignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,
    SHOW_ALWAYS,
    SHOW_ONLY_NEGATIVE
}
