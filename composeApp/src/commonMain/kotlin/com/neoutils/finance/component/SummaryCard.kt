package com.neoutils.finance.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.screen.transactions.BalanceOverview
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.TextLight1

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

@Composable
fun SummaryCard(
    balanceOverview: BalanceOverview,
    modifier: Modifier = Modifier,
    onEditBalance: (() -> Unit)? = null
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
            SummaryRow(
                label = "Saldo Inicial",
                amount = balanceOverview.initialBalance,
                color = colorScheme.onSurface
            )

            SummaryRow(
                label = "Entradas",
                amount = balanceOverview.income,
                color = Income
            )

            SummaryRow(
                label = "Despesas",
                amount = balanceOverview.expense,
                color = Expense
            )

            if (balanceOverview.adjustment != 0.0) {
                SummaryRow(
                    label = "Ajustes",
                    amount = balanceOverview.adjustment,
                    color = Adjustment
                )
            }

            HorizontalDivider()

            SummaryRow(
                label = "Saldo Final",
                amount = balanceOverview.finalBalance,
                color = colorScheme.onSurface,
                config = SummaryRowConfig.Total,
                onEditClick = onEditBalance
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
    config: SummaryRowConfig = SummaryRowConfig.Default
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = label,
            style = config.labelStyle
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = if (onEditClick != null) {
                Modifier.clickable { onEditClick() }
            } else {
                Modifier
            }
        ) {
           if (onEditClick != null) {
               Icon(
                   imageVector = Icons.Rounded.ModeEdit,
                   contentDescription = null,
                   tint = color.copy(alpha = 0.5f),
                   modifier = Modifier.size(16.dp)
               )
            }

            Text(
                text = amount.toMoneyFormat(),
                style = config.amountStyle.copy(color = color)
            )
        }
    }
}