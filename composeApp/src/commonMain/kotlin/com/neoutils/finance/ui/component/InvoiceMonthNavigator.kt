@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finance.domain.model.InvoiceMonthSelection
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth

@Composable
fun InvoiceMonthNavigator(
    selection: InvoiceMonthSelection,
    minDueMonth: YearMonth,
    onNavigate: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val canNavigatePrevious = selection.dueMonth > minDueMonth

    OutlinedTextField(
        value = selection.label,
        onValueChange = {},
        readOnly = true,
        label = {
            Text(text = "Fatura")
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Receipt,
                tint = selection.statusColor ?: colorScheme.primary,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onNavigate(selection.dueMonth.minusMonth())
                    },
                    enabled = canNavigatePrevious
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (canNavigatePrevious) colorScheme.primary else colorScheme.outline
                    )
                }

                IconButton(
                    onClick = {
                        onNavigate(selection.dueMonth.plusMonth())
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colorScheme.primary
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    )
}

