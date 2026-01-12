package com.neoutils.finance.ui.modal.addCreditCard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.util.stringUiText
import com.neoutils.finance.util.DayInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import com.neoutils.finance.util.Validation
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel

class AddCreditCardModal : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<AddCreditCardViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val name = rememberTextFieldState(uiState.forms.name.text)
        val limit = rememberTextFieldState(uiState.forms.limit)
        val closingDayField = rememberTextFieldState(uiState.forms.closingDay)
        val dueDayField = rememberTextFieldState(uiState.forms.dueDay)

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(AddCreditCardAction.NameChanged(value))
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { limit.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(AddCreditCardAction.LimitChanged(value))
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { closingDayField.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(AddCreditCardAction.ClosingDayChanged(value))
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { dueDayField.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(AddCreditCardAction.DueDayChanged(value))
                }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                trailingIcon = when (uiState.forms.name.validation) {
                    Validation.Validating -> {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    else -> null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                isError = uiState.forms.name.validation is Validation.Error,
                supportingText = when (val validation = uiState.forms.name.validation) {
                    is Validation.Error -> {
                        {
                            Text(text = stringUiText(validation.error))
                        }
                    }

                    else -> null
                },
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth(),
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
                    alwaysMinimize = uiState.forms.closingDayCalc != null
                ),
                label = {
                    Text(text = "Dia de Fechamento")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                placeholder = uiState.forms.closingDayCalc?.let { hint ->
                    {
                        Text(text = hint.toString())
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
                    alwaysMinimize = uiState.forms.dueDayCalc != null
                ),
                label = {
                    Text(text = "Dia de Vencimento")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                placeholder = uiState.forms.dueDayCalc?.let { hint ->
                    {
                        Text(text = hint.toString())
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
                    viewModel.onAction(AddCreditCardAction.Submit)
                },
                enabled = uiState.canSubmit,
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
}
