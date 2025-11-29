package com.neoutils.finance.modal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.component.MoneyInputTransformation
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class EditBalanceModal(
    private val currentBalance: Double,
    private val onConfirm: (Double) -> Unit
) : Modal {

    private val initialCents = ((currentBalance.takeUnless { it < 0 } ?: 0.0) * 100).toLong()

    @Composable
    override fun Content() {
        val manager = LocalModalManager.current
        val scope = rememberCoroutineScope()

        val balanceState = rememberTextFieldState(formatMoney(initialCents))

        val newBalance by remember {
            derivedStateOf {
                parseMoneyToDouble(balanceState.text.toString())
            }
        }

        val adjustment by remember {
            derivedStateOf {
                newBalance - currentBalance
            }
        }

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
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Editar Saldo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = adjustment,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                ) { adjustment ->
                    if (adjustment != 0.0) {
                        Adjustment(
                            adjustment = adjustment,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth())
                    }
                }

                OutlinedTextField(
                    state = balanceState,
                    inputTransformation = MoneyInputTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = colorScheme.surfaceContainerHighest,
                        focusedBorderColor = Adjustment,
                        cursorColor = Adjustment,
                        selectionColors = TextSelectionColors(
                            handleColor = Adjustment,
                            backgroundColor = Adjustment.copy(alpha = 0.4f)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { manager.dismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Cancelar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                onConfirm(newBalance)
                                manager.dismiss()
                            }
                        },
                        enabled = balanceState.text.isNotBlank() && newBalance != currentBalance,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Adjustment
                        ),
                    ) {
                        Text(
                            text = "Salvar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Adjustment(
        adjustment: Double,
        modifier: Modifier = Modifier
    ) = Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (adjustment > 0) {
                Income.copy(alpha = 0.1f)
            } else {
                Expense.copy(alpha = 0.1f)
            },
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (adjustment > 0) {
                        Icons.Default.ArrowUpward
                    } else {
                        Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    tint = if (adjustment > 0) Income else Expense,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (adjustment > 0) "Entrada" else "Saída",
                    fontSize = 16.sp,
                )
            }

            Text(
                text = "${if (adjustment > 0) "+" else ""}${adjustment.toMoneyFormat()}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (adjustment > 0) Income else Expense
            )
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
