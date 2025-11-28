package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.screen.transactions.BalanceOverview
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.TextLight1

@Composable
fun SummaryCard(
    balanceOverview: BalanceOverview,
    modifier: Modifier = Modifier
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

            HorizontalDivider()

            SummaryRow(
                label = "Saldo Final",
                amount = balanceOverview.finalBalance,
                color = colorScheme.onSurface,
                isTotal = true
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

        Text(
            text = "R$ %.2f".format(amount),
            fontSize = if (isTotal) 20.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}