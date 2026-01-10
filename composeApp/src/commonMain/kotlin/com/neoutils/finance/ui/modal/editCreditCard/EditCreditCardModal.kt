package com.neoutils.finance.ui.modal.editCreditCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.util.DayInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class EditCreditCardModal(
    private val creditCard: CreditCard
) : ModalBottomSheet() {

    companion object {
        private const val DEFAULT_DAYS_DIFFERENCE = 8
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditCreditCardViewModel> {
            parametersOf(creditCard.id)
        }

        val name = rememberTextFieldState(creditCard.name)
        val limit = rememberTextFieldState(formatMoneyFromDouble(creditCard.limit))

        val closingDayField = rememberTextFieldState(creditCard.closingDay.toString())
        val dueDayField = rememberTextFieldState(creditCard.dueDay.toString())

        var closingDayCalc by remember { mutableStateOf<Int?>(null) }
        var dueDayCalc by remember { mutableStateOf<Int?>(null) }

        val closingDay by remember {
            derivedStateOf {
                closingDayField.text.toString().toIntOrNull() ?: closingDayCalc
            }
        }

        val dueDay by remember {
            derivedStateOf {
                dueDayField.text.toString().toIntOrNull() ?: dueDayCalc
            }
        }

        val newLimit by remember {
            derivedStateOf { parseMoneyToDouble(limit.text.toString()) }
        }

        val isValid by remember {
            derivedStateOf {
                name.text.isNotBlank() && newLimit >= 0.0 && closingDay != null && dueDay != null
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow {
                closingDayField.text.toString()
            }.collect {
                val parsed = it.toIntOrNull()
                dueDayCalc = parsed?.let {
                    calculateDueDay(parsed)
                }
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow {
                dueDayField.text.toString()
            }.collect {
                val parsed = it.toIntOrNull()
                closingDayCalc = parsed?.let {
                    calculateClosingDay(parsed)
                }
            }
        }

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
                keyboardOptions = KeyboardOptions(
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                inputTransformation = MoneyInputTransformation(),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = closingDayField,
                labelPosition = TextFieldLabelPosition.Attached(
                    alwaysMinimize = closingDayCalc != null
                ),
                label = { Text(text = "Dia de Fechamento") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                placeholder = {
                    if (closingDayCalc != null) {
                        Text(text = closingDayCalc.toString())
                    }
                },
                inputTransformation = DayInputTransformation(),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = dueDayField,
                labelPosition = TextFieldLabelPosition.Attached(
                    alwaysMinimize = dueDayCalc != null
                ),
                label = { Text(text = "Dia de Vencimento") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                placeholder = {
                    if (dueDayCalc != null) {
                        Text(text = dueDayCalc.toString())
                    }
                },
                inputTransformation = DayInputTransformation(),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Button(
                onClick = {
                    viewModel.update(
                        CreditCardForm(
                            name = name.text.toString(),
                            limit = parseMoneyToDouble(limit.text.toString()),
                            closingDay = closingDay,
                            dueDay = dueDay,
                        )
                    )
                },
                enabled = isValid,
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

    private fun calculateDueDay(closingDay: Int): Int {
        return ((closingDay - 1 + DEFAULT_DAYS_DIFFERENCE) % 31) + 1
    }

    private fun calculateClosingDay(dueDay: Int): Int {
        return ((dueDay - 1 - DEFAULT_DAYS_DIFFERENCE + 31) % 31) + 1
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
        val centavos = (intValue % 100).toString().padStart(2, '0')
        return "R$ $reais,$centavos"
    }
}
