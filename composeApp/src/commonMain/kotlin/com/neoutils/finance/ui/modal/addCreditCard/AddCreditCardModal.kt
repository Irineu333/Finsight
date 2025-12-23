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
import org.koin.compose.viewmodel.koinViewModel

class AddCreditCardModal : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<AddCreditCardViewModel>(key = key)

        val name = rememberTextFieldState()
        val limit = rememberTextFieldState("R$ 0,00")
        val closingDay = rememberTextFieldState()

        val parsedClosingDay by remember {
            derivedStateOf { closingDay.text.toString().toIntOrNull() }
        }

        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
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
                state = closingDay,
                label = { Text(text = "Dia de Fechamento") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
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
                            closingDay = parsedClosingDay,
                        )
                    )
                },
                enabled = name.text.isNotBlank(),
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
