package com.neoutils.finance.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.screen.transactions.TransactionsUiState
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.InvoicePayment
import com.neoutils.finance.ui.theme.TextLight1
import kotlin.math.absoluteValue

@Composable
fun CreditCardTotalSummaryCard(
    overview: TransactionsUiState.CreditCardOverview,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandClick: (() -> Unit)? = null
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

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
                    text = "Cartões de Crédito",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )

                if (onExpandClick != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onExpandClick() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${overview.invoices.size} faturas",
                            fontSize = 14.sp,
                            color = colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationAngle)
                        )
                    }
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
                        label = "Gastos",
                        amount = currentOverview.expense,
                        color = Expense,
                        signDisplay = CreditCardTotalSignDisplay.ALWAYS_NEGATIVE
                    )

                    if (currentOverview.mustShowAdvancePayment) {
                        CreditCardTotalRow(
                            label = "Adiantamentos",
                            amount = currentOverview.advancePayment,
                            color = InvoicePayment,
                            signDisplay = CreditCardTotalSignDisplay.ALWAYS_POSITIVE
                        )
                    }

                    HorizontalDivider()

                    CreditCardTotalRow(
                        label = "Faturas",
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
