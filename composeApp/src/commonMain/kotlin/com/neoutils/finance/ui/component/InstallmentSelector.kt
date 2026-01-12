@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.extension.toMoneyFormat

@Composable
fun InstallmentSelector(
    selectedInstallments: Int,
    totalAmount: Double,
    onInstallmentsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxInstallments: Int = 12
) {
    var expanded by remember { mutableStateOf(false) }

    val displayValue = if (selectedInstallments == 1) {
        "À vista"
    } else {
        val installmentAmount = totalAmount / selectedInstallments
        "${selectedInstallments}x de ${installmentAmount.toMoneyFormat()}"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = "Parcelas")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CreditScore,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
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
            (1..maxInstallments).forEach { installment ->
                val installmentLabel = if (installment == 1) {
                    "À vista"
                } else {
                    val amount = totalAmount / installment
                    "${installment}x de ${amount.toMoneyFormat()}"
                }

                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = installmentLabel,
                                fontSize = 14.sp
                            )
                        }
                    },
                    onClick = {
                        onInstallmentsSelected(installment)
                        expanded = false
                    }
                )
            }
        }
    }
}
