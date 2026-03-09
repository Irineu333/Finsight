@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

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
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_navigator_label
import com.neoutils.finsight.extension.toLabel
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import org.jetbrains.compose.resources.stringResource

@Composable
fun InvoiceMonthNavigator(
    selection: InvoiceMonthSelection,
    onNavigate: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    OutlinedTextField(
        value = selection.toLabel(),
        onValueChange = {},
        readOnly = true,
        label = {
            Text(text = label.ifEmpty { stringResource(Res.string.invoice_navigator_label) })
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
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = colorScheme.primary
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
        colors = if (selection.isBlocked) {
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = colorScheme.error.copy(alpha = 0.5f),
                focusedBorderColor = colorScheme.error
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    )
}
