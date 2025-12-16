package com.neoutils.finance.ui.component

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
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.screen.transactions.TransactionsUiState
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.InvoicePayment
import com.neoutils.finance.ui.theme.TextLight1

@Composable
fun CreditCardSummaryCard(
        overview: TransactionsUiState.CreditCardOverview,
        modifier: Modifier = Modifier
) {
    Card(
            modifier = modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = colorScheme.surfaceContainer,
                    ),
            shape = RoundedCornerShape(16.dp),
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "Cartão de Crédito",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
            )

            AnimatedContent(
                    targetState = overview,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { overview ->
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CreditCardSummaryRow(
                            label = "Gastos",
                            amount = overview.expense,
                            color = Expense,
                            signDisplay = CreditCardSignDisplay.ALWAYS_NEGATIVE
                    )

                    if (overview.mustShowInvoicePayment) {
                        CreditCardSummaryRow(
                                label = "Fatura Paga",
                                amount = overview.invoicePayment,
                                color = InvoicePayment,
                                signDisplay = CreditCardSignDisplay.ALWAYS_NEGATIVE
                        )
                    }

                    if (overview.mustShowAdvancePayment) {
                        CreditCardSummaryRow(
                                label = "Adiantamentos",
                                amount = overview.advancePayment,
                                color = InvoicePayment,
                                signDisplay = CreditCardSignDisplay.SHOW_ALWAYS
                        )
                    }

                    if (overview.mustShowAdjustment) {
                        CreditCardSummaryRow(
                                label = "Ajustes",
                                amount = overview.adjustment,
                                color = Adjustment,
                                signDisplay = CreditCardSignDisplay.SHOW_ALWAYS
                        )
                    }

                    HorizontalDivider()

                    val total =
                            overview.expense +
                                    overview.invoicePayment +
                                    overview.advancePayment +
                                    overview.adjustment
                    CreditCardSummaryRow(
                            label = "Total",
                            amount = total,
                            color = colorScheme.onSurface,
                            signDisplay = CreditCardSignDisplay.ALWAYS_NEGATIVE,
                            isTotal = true
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCardSummaryRow(
        label: String,
        amount: Double,
        color: Color,
        modifier: Modifier = Modifier,
        signDisplay: CreditCardSignDisplay = CreditCardSignDisplay.SHOW_ONLY_NEGATIVE,
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

        val formattedAmount =
                when (signDisplay) {
                    CreditCardSignDisplay.ALWAYS_POSITIVE -> "+${amount.toMoneyFormat()}"
                    CreditCardSignDisplay.ALWAYS_NEGATIVE -> "-${amount.toMoneyFormat()}"
                    CreditCardSignDisplay.SHOW_ALWAYS -> {
                        when {
                            amount > 0 -> "+${amount.toMoneyFormat()}"
                            amount < 0 -> amount.toMoneyFormat()
                            else -> amount.toMoneyFormat()
                        }
                    }
                    CreditCardSignDisplay.SHOW_ONLY_NEGATIVE -> {
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

private enum class CreditCardSignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,
    SHOW_ALWAYS,
    SHOW_ONLY_NEGATIVE
}
