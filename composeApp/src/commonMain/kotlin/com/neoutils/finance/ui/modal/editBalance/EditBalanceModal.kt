package com.neoutils.finance.ui.modal.editBalance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.ui.theme.TextLight1
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.MoneyInputTransformation
import kotlinx.datetime.YearMonth
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
class EditBalanceModal(
    private val type: Type = Type.FINAL,
    private val targetMonth: YearMonth? = null,
    private val currentBalance: Double,
) : ModalBottomSheet {

    private val initialCents = (currentBalance * 100).toLong()

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditBalanceViewModel>(key = key) {
            parametersOf(type, targetMonth, currentBalance)
        }

        val manager = LocalModalManager.current

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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = type.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            if (targetMonth != null) {
                Text(
                    text = formats.yearMonth.format(targetMonth),
                    fontSize = 14.sp,
                    color = TextLight1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancelar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        viewModel.adjustBalance(newBalance)
                    },
                    enabled = balanceState.text.isNotBlank() && newBalance != currentBalance,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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
        val isNegative = cents < 0
        val absoluteCents = abs(cents)

        val reais = absoluteCents / 100
        val centavos = absoluteCents % 100

        val reaisFormatted = reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()

        val formatted = "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"

        return if (isNegative) "-$formatted" else formatted
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val isNegative = formatted.startsWith("-")

        val digitsOnly = formatted
            .replace("-", "")
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        val value = digitsOnly.toDoubleOrNull() ?: 0.0

        return if (isNegative) -value else value
    }

    enum class Type(val title: String) {
        CURRENT("Editar Saldo Atual"),
        FINAL("Editar Saldo Final"),
        INITIAL("Editar Saldo Inicial")
    }
}