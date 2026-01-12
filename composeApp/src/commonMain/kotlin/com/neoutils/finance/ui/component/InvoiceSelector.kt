@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Invoice

@Composable
fun InvoiceSelector(
    invoices: List<Invoice>,
    selectedInvoice: Invoice?,
    onInvoiceSelected: (Invoice) -> Unit,
    onCreateFutureInvoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedInvoice?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = "Fatura")
            },
            leadingIcon = selectedInvoice?.let {
                {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        tint = it.status.color,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
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
                                text = invoice.label,
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

            HorizontalDivider()

            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Criar fatura",
                            fontSize = 14.sp,
                            color = colorScheme.primary
                        )
                    }
                },
                onClick = {
                    onCreateFutureInvoice()
                    expanded = false
                }
            )
        }
    }
}

