@file:OptIn(FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
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
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.extension.toMoneyFormatWithSign
import com.neoutils.finance.ui.icons.CategoryIcon
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.InvoicePayment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats

private val formats = DateFormats()

@Composable
fun TransactionCard(
    transaction: Transaction,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Card(
    onClick = onClick,
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
                Transaction.Type.INCOME -> Income.copy(alpha = 0.2f)
                Transaction.Type.EXPENSE -> Expense.copy(alpha = 0.2f)
                Transaction.Type.ADJUSTMENT -> Adjustment.copy(alpha = 0.2f)
                Transaction.Type.INVOICE_PAYMENT,
                Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment.copy(alpha = 0.2f)
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = category?.let {
                        CategoryIcon.fromKey(category.key).icon
                    } ?: when (transaction.type) {
                        Transaction.Type.INCOME -> Icons.Default.ShoppingCart
                        Transaction.Type.EXPENSE -> Icons.Default.Receipt
                        Transaction.Type.ADJUSTMENT -> Icons.Default.Edit
                        Transaction.Type.INVOICE_PAYMENT,
                        Transaction.Type.ADVANCE_PAYMENT -> Icons.Default.Receipt
                    },
                    contentDescription = null,
                    tint = when (transaction.type) {
                        Transaction.Type.INCOME -> Income
                        Transaction.Type.EXPENSE -> Expense
                        Transaction.Type.ADJUSTMENT -> Adjustment
                        Transaction.Type.INVOICE_PAYMENT,
                        Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = getTitle(transaction, category),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formats.dayMonthYear.format(transaction.date),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = when (transaction.type) {
                Transaction.Type.INCOME -> transaction.amount.toMoneyFormat()
                Transaction.Type.EXPENSE -> transaction.amount.toMoneyFormat()
                Transaction.Type.ADJUSTMENT -> transaction.amount.toMoneyFormatWithSign()
                Transaction.Type.INVOICE_PAYMENT,
                Transaction.Type.ADVANCE_PAYMENT -> transaction.amount.toMoneyFormatWithSign()
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (transaction.type) {
                Transaction.Type.INCOME -> Income
                Transaction.Type.EXPENSE -> Expense
                Transaction.Type.ADJUSTMENT -> Adjustment
                Transaction.Type.INVOICE_PAYMENT,
                Transaction.Type.ADVANCE_PAYMENT -> InvoicePayment
            }
        )
    }
}

private fun getTitle(
    transaction: Transaction,
    category: Category?
) = (transaction.title ?: category?.name) ?: "Título"
