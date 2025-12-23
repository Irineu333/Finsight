package com.neoutils.finance.ui.modal.editCreditCardLimit

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.MoneyInputTransformation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class EditCreditCardLimitModal(
    private val creditCard: CreditCard
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditCreditCardLimitViewModel>(key = key) {
            parametersOf(creditCard.id)
        }

        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val limit = rememberTextFieldState(formatMoneyFromDouble(creditCard.limit))

        val newLimit by remember {
            derivedStateOf {
                parseMoneyToDouble(limit.text.toString())
            }
        }

        val availableLimit by remember {
            derivedStateOf {
                (newLimit - uiState.currentBill).coerceAtLeast(minimumValue = 0.0)
            }
        }

        val showPreview by remember {
            derivedStateOf {
                uiState.currentBill > 0 && availableLimit != newLimit
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Editar Limite",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = availableLimit to showPreview,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { (available, show) ->
                if (show) {
                    AvailableLimitPreview(
                        availableLimit = available,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth())
                }
            }

            OutlinedTextField(
                state = limit,
                inputTransformation = MoneyInputTransformation(),
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.updateLimit(parseMoneyToDouble(limit.text.toString()))
                },
                enabled = isValidLimit(limit.text.toString()),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Salvar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    private fun AvailableLimitPreview(
        availableLimit: Double,
        modifier: Modifier = Modifier
    ) {
        val color = if (availableLimit >= 0) Income else Expense

        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f),
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
                Text(
                    text = "Limite Disponível",
                    fontSize = 16.sp,
                )

                Text(
                    text = availableLimit.toMoneyFormat(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }

    private fun isValidLimit(amount: String): Boolean {
        if (amount.isEmpty()) return false
        return parseMoneyToDouble(amount) >= 0.0
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val digitsOnly = formatted
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }

    private fun formatMoneyFromDouble(value: Double): String {
        val intValue = (value * 100).toInt()
        val reais = intValue / 100
        val centavos = intValue % 100
        return "R$ %d,%02d".format(reais, centavos)
    }
}
