package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal

class EditBalanceModal(
    private val currentBalance: Double,
    private val onConfirm: (Double) -> Unit
) : Modal {

    @Composable
    override fun Content() {
        val manager = LocalModalManager.current
        val initialCents = (currentBalance * 100).toLong()
        val balanceState = rememberTextFieldState(formatMoney(initialCents))

        AlertDialog(
            onDismissRequest = { manager.dismiss() },
            title = {
                Text(text = "Editar Saldo")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Informe o saldo real da sua conta:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        state = balanceState,
                        label = { Text("Saldo Real") },
                        inputTransformation = MoneyInputTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newBalance = parseMoneyToDouble(balanceState.text.toString())
                        onConfirm(newBalance)
                        manager.dismiss()
                    },
                    enabled = balanceState.text.isNotBlank()
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { manager.dismiss() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    private fun formatMoney(cents: Long): String {
        val reais = cents / 100
        val centavos = cents % 100

        val reaisFormatted = reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()

        return "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val digitsOnly = formatted
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }
}
