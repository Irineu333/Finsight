@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_selector_label
import com.neoutils.finsight.ui.extension.toLabel
import org.jetbrains.compose.resources.stringResource

@Composable
fun InvoiceSelector(
    invoices: List<Invoice>,
    invoice: Invoice?,
    onInvoiceSelected: (Invoice) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (invoices.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = invoice?.toLabel().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = stringResource(Res.string.invoice_selector_label))
            },
            leadingIcon = invoice?.let {
                {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = it.status.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = invoices.isNotEmpty(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .animateContentSize()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            invoices.forEach { invoice ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = invoice.status.color,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = invoice.toLabel(),
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        onInvoiceSelected(invoice)
                        expanded = false
                    }
                )
            }
        }
    }
}
