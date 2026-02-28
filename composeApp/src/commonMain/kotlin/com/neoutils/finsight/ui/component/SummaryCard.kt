package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.summary_card_adjustments
import com.neoutils.finsight.resources.summary_card_current_balance
import com.neoutils.finsight.resources.summary_card_final_balance
import com.neoutils.finsight.resources.summary_card_income
import com.neoutils.finsight.resources.summary_card_initial_balance
import com.neoutils.finsight.resources.summary_card_invoices
import com.neoutils.finsight.resources.summary_card_outgoing
import com.neoutils.finsight.resources.summary_card_see_invoices
import org.jetbrains.compose.resources.stringResource

@Composable
fun SummaryCard(
    balanceOverview: TransactionsUiState.BalanceOverview,
    modifier: Modifier = Modifier,
    isCurrentMonth: Boolean = false,
    onEditBalance: (() -> Unit)? = null,
    onEditInitialBalance: (() -> Unit)? = null,
    onInvoiceClick: (() -> Unit)? = null
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
            AnimatedContent(
                targetState = balanceOverview,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { balanceOverview ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryRow(
                        label = stringResource(Res.string.summary_card_initial_balance),
                        amount = balanceOverview.initialBalance,
                        color = colorScheme.onSurface,
                        onEditClick = onEditInitialBalance,
                        signDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
                    )

                    SummaryRow(
                        label = stringResource(Res.string.summary_card_income),
                        amount = balanceOverview.income,
                        color = Income,
                        signDisplay = SignDisplay.ALWAYS_POSITIVE
                    )

                    SummaryRow(
                        label = stringResource(Res.string.summary_card_outgoing),
                        amount = balanceOverview.expense,
                        color = Expense,
                        signDisplay = SignDisplay.ALWAYS_NEGATIVE
                    )

                    if (balanceOverview.mustShowPayment) {
                        SummaryRow(
                            label = stringResource(Res.string.summary_card_invoices),
                            amount = balanceOverview.payment,
                            color = InvoicePayment,
                            onNavigateClick = onInvoiceClick,
                            signDisplay = SignDisplay.ALWAYS_NEGATIVE
                        )
                    }

                    if (balanceOverview.mustShowAccountAdjustment) {
                        SummaryRow(
                            label = stringResource(Res.string.summary_card_adjustments),
                            amount = balanceOverview.adjustment,
                            color = Adjustment,
                            signDisplay = SignDisplay.SHOW_ALWAYS
                        )
                    }
                }
            }

            HorizontalDivider()

            SummaryRow(
                label = if (isCurrentMonth) stringResource(Res.string.summary_card_current_balance) else stringResource(Res.string.summary_card_final_balance),
                amount = balanceOverview.finalBalance,
                color = colorScheme.onSurface,
                config = SummaryRowConfig.Total,
                onEditClick = onEditBalance,
                signDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
    onNavigateClick: (() -> Unit)? = null,
    config: SummaryRowConfig = SummaryRowConfig.Default,
    signDisplay: SignDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.then(
                if (onNavigateClick != null) {
                    Modifier.clickable { onNavigateClick() }
                } else {
                    Modifier
                }
            )
        ) {
            Text(
                text = label,
                style = config.labelStyle
            )

            if (onNavigateClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.summary_card_see_invoices),
                    tint = config.labelStyle.color.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
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
                    contentDescription = null,
                    tint = color.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            val formattedAmount = when (signDisplay) {
                SignDisplay.ALWAYS_POSITIVE -> "+${amount.toMoneyFormat()}"
                SignDisplay.ALWAYS_NEGATIVE -> "-${amount.toMoneyFormat()}"
                SignDisplay.SHOW_ALWAYS -> {
                    when {
                        amount > 0 -> "+${amount.toMoneyFormat()}"
                        amount < 0 -> amount.toMoneyFormat() // já tem o sinal negativo
                        else -> amount.toMoneyFormat()
                    }
                }

                SignDisplay.SHOW_ONLY_NEGATIVE -> {
                    if (amount < 0) amount.toMoneyFormat() else amount.toMoneyFormat()
                }
            }

            Text(
                text = formattedAmount,
                style = config.amountStyle.copy(color = color)
            )
        }
    }
}

enum class SignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,
    SHOW_ALWAYS,
    SHOW_ONLY_NEGATIVE
}

data class SummaryRowConfig(
    val labelStyle: TextStyle,
    val amountStyle: TextStyle
) {
    companion object {
        val Default
            @Composable
            get() = SummaryRowConfig(
                labelStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextLight1
                ),
                amountStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )

        val Total
            @Composable
            get() = SummaryRowConfig(
                labelStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                ),
                amountStyle = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
    }
}
