package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class EditBalanceModal(
    private val currentBalance: Double,
    private val onConfirm: (Double) -> Unit
) : Modal {

    private val suggestedBalance = currentBalance.takeUnless { it < 0 } ?: 0.0
    private val initialCents = (suggestedBalance * 100).toLong()

    @Composable
    override fun Content() {
        val manager = LocalModalManager.current
        val scope = rememberCoroutineScope()

        val balanceState = rememberTextFieldState(formatMoney(initialCents))

        ModalBottomSheet(
            onDismissRequest = { manager.dismiss() },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Editar Saldo",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Informe o saldo real da sua conta:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    state = balanceState,
                    label = { Text("Saldo Real") },
                    inputTransformation = MoneyInputTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val newBalance = parseMoneyToDouble(balanceState.text.toString())
                            onConfirm(newBalance)
                            manager.dismiss()
                        }
                    },
                    enabled = balanceState.text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Confirmar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
