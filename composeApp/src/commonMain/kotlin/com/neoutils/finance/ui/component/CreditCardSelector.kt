@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.model.InvoiceUi
import com.neoutils.finance.util.DateFormats

private val formats = DateFormats()

@Composable
fun CreditCardSelector(
    creditCards: List<CreditCard>,
    creditCard: CreditCard?,
    invoice: InvoiceUi?,
    onCreditCardSelected: (CreditCard?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (creditCards.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = creditCard?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = "Cartão")
            },
            leadingIcon = creditCard?.let {
                {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            supportingText = invoice?.let {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formats.yearMonth.format(invoice.dueMonth),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )

                        if (invoice.creditCard.limit != 0.0) {
                            Text(
                                text = " • ${it.availableLimit.toMoneyFormat()}",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            enabled = creditCards.isNotEmpty(),
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
            creditCards.forEach { creditCard ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = creditCard.name,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        onCreditCardSelected(creditCard)
                        expanded = false
                    }
                )
            }
        }
    }
}

