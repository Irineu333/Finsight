package com.neoutils.finance.ui.modal.editCreditCard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.MoneyInputTransformation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class EditCreditCardModal(
    private val creditCardId: Long
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel =
            koinViewModel<EditCreditCardViewModel>(key = key) { parametersOf(creditCardId) }

        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return
        }

        val name = rememberTextFieldState()
        val limit = rememberTextFieldState()

        LaunchedEffect(uiState.currentName, uiState.currentLimit) {
            name.edit { replace(0, length, uiState.currentName) }
            limit.edit { replace(0, length, formatMoneyFromDouble(uiState.currentLimit)) }
        }

        val newLimit by remember { derivedStateOf { parseMoneyToDouble(limit.text.toString()) } }

        val availableLimit by remember {
            derivedStateOf { (newLimit - uiState.currentBill).coerceAtLeast(minimumValue = 0.0) }
        }

        val showPreview by remember {
            derivedStateOf { uiState.currentBill > 0 && availableLimit != newLimit }
        }

        val isValid by remember { derivedStateOf { name.text.isNotBlank() && newLimit >= 0.0 } }

        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Editar Cartão",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                state = name,
                label = { Text(text = "Nome do Cartão") },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = limit,
                label = { Text(text = "Limite") },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                inputTransformation = MoneyInputTransformation(),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Button(
                onClick = {
                    viewModel.save(
                        name = name.text.toString(),
                        limit = parseMoneyToDouble(limit.text.toString())
                    )
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(text = "Salvar", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }

    @Composable
    private fun AvailableLimitPreview(availableLimit: Double, modifier: Modifier = Modifier) {
        val color = if (availableLimit >= 0) Income else Expense

        Card(
            modifier = modifier,
            colors =
                CardDefaults.cardColors(
                    containerColor = color.copy(alpha = 0.1f),
                    contentColor = Color.White,
                ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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

    private fun parseMoneyToDouble(formatted: String): Double {
        val digitsOnly = formatted.replace("R$", "").replace(".", "").replace(",", ".").trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }

    private fun formatMoneyFromDouble(value: Double): String {
        val intValue = (value * 100).toInt()
        val reais = intValue / 100
        val centavos = intValue % 100
        return "R$ %d,%02d".format(reais, centavos)
    }
}
