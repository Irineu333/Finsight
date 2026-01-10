package com.neoutils.finance.ui.modal.addCreditCard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.util.DayInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel

class AddCreditCardModal : ModalBottomSheet() {

    companion object {
        private const val DEFAULT_DAYS_DIFFERENCE = 8
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<AddCreditCardViewModel>()

        val name = rememberTextFieldState()
        val limit = rememberTextFieldState("R$ 0,00")

        val closingDayField = rememberTextFieldState()
        val dueDayField = rememberTextFieldState()

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

        LaunchedEffect(Unit) {
            snapshotFlow {
                closingDayField.text.toString()
            }.collect {
                val closingDay = it.toIntOrNull()
                dueDayCalc = closingDay?.let {
                    calculateDueDay(closingDay)
                }
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow {
                dueDayField.text.toString()
            }.collect {
                val dueDay = it.toIntOrNull()
                closingDayCalc = dueDay?.let {
                    calculateClosingDay(dueDay)
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
                text = "Novo Cartão",
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
                    viewModel.addCreditCard(
                        CreditCardForm(
                            name = name.text.toString().trim(),
                            limit = parseMoneyToDouble(limit.text.toString()),
                            closingDay = closingDay,
                            dueDay = dueDay,
                        )
                    )
                },
                enabled = name.text.isNotBlank() && closingDay != null && dueDay != null,
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
        val digitsOnly =
            formatted
                .replace("R$", "")
                .replace(".", "")
                .replace(",", ".")
                .replace("-", "")
                .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }
}
