package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_total_advance_payments
import com.neoutils.finsight.resources.credit_card_total_expenses
import com.neoutils.finsight.resources.credit_card_total_invoice_plural
import com.neoutils.finsight.resources.credit_card_total_invoice_singular
import com.neoutils.finsight.resources.credit_card_total_invoices
import com.neoutils.finsight.resources.credit_card_total_title
import kotlin.math.absoluteValue
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreditCardTotalSummaryCard(
    overview: TransactionsUiState.CreditCardOverview,
    modifier: Modifier = Modifier,
    invoiceCount: Int = 0
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
                    text = stringResource(Res.string.credit_card_total_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )

                if (invoiceCount > 0) {
                    Text(
                        text = if (invoiceCount == 1) {
                            stringResource(Res.string.credit_card_total_invoice_singular, invoiceCount)
                        } else {
                            stringResource(Res.string.credit_card_total_invoice_plural, invoiceCount)
                        },
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant
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
                    CreditCardTotalRow(
                        label = stringResource(Res.string.credit_card_total_expenses),
                        amount = currentOverview.expense,
                        color = Expense,
                        signDisplay = CreditCardTotalSignDisplay.ALWAYS_NEGATIVE
                    )

                    if (currentOverview.mustShowAdvancePayment) {
                        CreditCardTotalRow(
                            label = stringResource(Res.string.credit_card_total_advance_payments),
                            amount = currentOverview.advancePayment,
                            color = InvoicePayment,
                            signDisplay = CreditCardTotalSignDisplay.ALWAYS_POSITIVE
                        )
                    }

                    HorizontalDivider()

                    CreditCardTotalRow(
                        label = stringResource(Res.string.credit_card_total_invoices),
                        amount = currentOverview.total,
                        color = colorScheme.onSurface,
                        signDisplay = CreditCardTotalSignDisplay.SHOW_ONLY_NEGATIVE,
                        isTotal = true
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCardTotalRow(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    signDisplay: CreditCardTotalSignDisplay = CreditCardTotalSignDisplay.SHOW_ONLY_NEGATIVE,
    isTotal: Boolean = false
) {
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
            CreditCardTotalSignDisplay.ALWAYS_POSITIVE -> {
                "+${amount.absoluteValue.toMoneyFormat()}"
            }
            CreditCardTotalSignDisplay.ALWAYS_NEGATIVE -> {
                "-${amount.absoluteValue.toMoneyFormat()}"
            }
            CreditCardTotalSignDisplay.SHOW_ONLY_NEGATIVE -> {
                if (amount < 0) amount.toMoneyFormat() else amount.toMoneyFormat()
            }
        }

        Text(
            text = formattedAmount,
            fontSize = if (isTotal) 20.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private enum class CreditCardTotalSignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,
    SHOW_ONLY_NEGATIVE
}
