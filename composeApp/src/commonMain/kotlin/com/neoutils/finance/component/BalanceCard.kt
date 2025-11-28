package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.ui.theme.Expense as ExpenseColor
import com.neoutils.finance.ui.theme.Income as IncomeColor

data class BalanceCardConfig(
    val icon: ImageVector?,
    val title: String,
    val style: TextStyle,
    val titleStyle: TextStyle,
    val padding: PaddingValues,
    val container: Color,
    val shape: Shape
) {
    companion object {
        val Default
            @Composable
            get() = BalanceCardConfig(
                title = "Saldo Atual",
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                ),
                titleStyle = TextStyle(
                    fontSize = 16.sp,
                    color = colorScheme.onSurfaceVariant
                ),
                padding = PaddingValues(24.dp),
                container = colorScheme.surfaceContainer,
                shape = shapes.large,
                icon = null,
            )

        val Income
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.ArrowUpward,
                title = "Receitas",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                ),
                titleStyle = TextStyle(
                    fontSize = 14.sp,
                    color = IncomeColor
                ),
                padding = PaddingValues(16.dp),
                container = IncomeColor.copy(alpha = 0.15f),
                shape = shapes.large
            )

        val Expense
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.ArrowDownward,
                title = "Despesas",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                ),
                titleStyle = TextStyle(
                    fontSize = 14.sp,
                    color = ExpenseColor
                ),
                padding = PaddingValues(16.dp),
                container = ExpenseColor.copy(alpha = 0.15f),
                shape = shapes.large
            )
    }
}

@Composable
fun BalanceCard(
    balance: Double,
    modifier: Modifier = Modifier,
    config: BalanceCardConfig = BalanceCardConfig.Default
) = Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = config.container
    ),
    shape = config.shape,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(config.padding)
    ) {
        if (config.icon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                 Icon(
                    imageVector = config.icon,
                    contentDescription = null,
                    tint = config.titleStyle.color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = config.title,
                    style = config.titleStyle,
                )
            }
        } else {
            Text(
                text = config.title,
                style = config.titleStyle,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "R$ %.2f".format(balance),
            style = config.style,
        )
    }
}