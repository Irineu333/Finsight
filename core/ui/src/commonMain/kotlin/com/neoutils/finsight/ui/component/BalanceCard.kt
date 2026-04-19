package com.neoutils.finsight.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.core.ui.resources.Res
import com.neoutils.finsight.core.ui.resources.balance_card_credit_card_expense
import com.neoutils.finsight.core.ui.resources.balance_card_current_balance
import com.neoutils.finsight.core.ui.resources.balance_card_current_invoice
import com.neoutils.finsight.core.ui.resources.balance_card_expense
import com.neoutils.finsight.core.ui.resources.balance_card_income
import com.neoutils.finsight.core.ui.resources.balance_card_invoices
import com.neoutils.finsight.core.ui.resources.balance_card_invoice_payments
import com.neoutils.finsight.core.ui.resources.balance_card_pay_invoice
import com.neoutils.finsight.core.ui.resources.balance_card_pending_expense
import com.neoutils.finsight.core.ui.resources.balance_card_pending_income
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import org.jetbrains.compose.resources.stringResource
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.InvoicePayment as InvoicePaymentColor
import com.neoutils.finsight.ui.theme.Income as IncomeColor

@Composable
fun BalanceCard(
    balance: Double,
    modifier: Modifier = Modifier,
    config: BalanceCardConfig = BalanceCardConfig.Default,
    onEditClick: (() -> Unit)? = null,
    onPayClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val formatter = LocalCurrencyFormatter.current
    Card(
    modifier = modifier.then(
        if (onClick != null) {
            Modifier
                .clip(config.shape)
                .clickable { onClick() }
        } else {
            Modifier
        }
    ),
    colors = CardDefaults.cardColors(
        containerColor = config.container
    ),
    shape = config.shape,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(config.padding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            Text(
                text = formatter.format(balance),
                style = config.style,
            )

            if (onEditClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ModeEdit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = config.style.color.copy(alpha = 0.5f),
                )
            }
        }

        if (onPayClick != null) {
            OutlinedButton(
                onClick = onPayClick,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.primary
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = stringResource(Res.string.balance_card_pay_invoice),
                    fontSize = 14.sp
                )
            }
        }
    }
}
}

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
                title = stringResource(Res.string.balance_card_current_balance),
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
                title = stringResource(Res.string.balance_card_income),
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
                title = stringResource(Res.string.balance_card_expense),
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

        val Payment
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.CreditCard,
                title = stringResource(Res.string.balance_card_invoices),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                ),
                titleStyle = TextStyle(
                    fontSize = 14.sp,
                    color = InvoicePaymentColor
                ),
                padding = PaddingValues(16.dp),
                container = InvoicePaymentColor.copy(alpha = 0.15f),
                shape = shapes.large
            )

        val InvoicePayment
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.CreditCard,
                title = stringResource(Res.string.balance_card_invoice_payments),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                ),
                titleStyle = TextStyle(
                    fontSize = 14.sp,
                    color = InvoicePaymentColor
                ),
                padding = PaddingValues(16.dp),
                container = InvoicePaymentColor.copy(alpha = 0.15f),
                shape = shapes.large
            )

        val CreditCardExpense
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.ArrowDownward,
                title = stringResource(Res.string.balance_card_credit_card_expense),
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

        val PendingIncome
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.ArrowUpward,
                title = stringResource(Res.string.balance_card_pending_income),
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
                container = IncomeColor.copy(alpha = 0.1f),
                shape = shapes.large
            )

        val PendingExpense
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.ArrowDownward,
                title = stringResource(Res.string.balance_card_pending_expense),
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
                container = ExpenseColor.copy(alpha = 0.1f),
                shape = shapes.large
            )

        val CreditCard
            @Composable
            get() = BalanceCardConfig(
                icon = Icons.Default.CreditCard,
                title = stringResource(Res.string.balance_card_current_invoice),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                ),
                titleStyle = TextStyle(
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant
                ),
                padding = PaddingValues(20.dp),
                container = colorScheme.surfaceContainer,
                shape = shapes.large
            )
    }
}
