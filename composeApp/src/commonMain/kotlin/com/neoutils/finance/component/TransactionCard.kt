@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

private val dateFormat = LocalDate.Format {
    byUnicodePattern("dd/MM/yyyy")
}

@Composable
fun TransactionCard(
    transaction: TransactionEntry,
    modifier: Modifier = Modifier
) = Card(
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
        Surface(
            color = when (transaction.type) {
                TransactionEntry.Type.INCOME -> Income.copy(alpha = 0.2f)
                TransactionEntry.Type.EXPENSE -> Expense.copy(alpha = 0.2f)
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = when (transaction.type) {
                        TransactionEntry.Type.INCOME -> Income
                        TransactionEntry.Type.EXPENSE -> Expense
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = transaction.description,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = dateFormat.format(transaction.date),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = when (transaction.type) {
                TransactionEntry.Type.INCOME -> "+R$ %.2f".format(transaction.amount)
                TransactionEntry.Type.EXPENSE -> "-R$ %.2f".format(transaction.amount)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (transaction.type) {
                TransactionEntry.Type.INCOME -> Income
                TransactionEntry.Type.EXPENSE -> Expense
            }
        )
    }
}