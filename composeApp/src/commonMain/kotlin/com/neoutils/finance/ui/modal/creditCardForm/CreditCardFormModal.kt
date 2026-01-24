package com.neoutils.finance.ui.modal.creditCardForm

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
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
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.categoryForm.CategoryField
import com.neoutils.finance.ui.util.stringUiText
import com.neoutils.finance.util.DayInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import com.neoutils.finance.util.Validation
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CreditCardFormModal(
    private val creditCard: CreditCard? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CreditCardFormViewModel> { parametersOf(creditCard) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val name = rememberTextFieldState(uiState.form.name)
        val limit = rememberTextFieldState(uiState.form.limit)
        val closingDay = rememberTextFieldState(uiState.form.closingDayUser)
        val dueDay = rememberTextFieldState(uiState.form.dueDayUser)

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(CreditCardFormAction.NameChanged(value))
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { limit.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(CreditCardFormAction.LimitChanged(value))
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { closingDay.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(CreditCardFormAction.ClosingDayChanged(value))
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { dueDay.text.toString() }
                .drop(1)
                .collect { value ->
                    viewModel.onAction(CreditCardFormAction.DueDayChanged(value))
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
                text = if (uiState.isEditMode) "Editar Cartão" else "Novo Cartão",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                state = name,
                label = { Text(text = "Nome do Cartão") },
                trailingIcon = when (uiState.validation[CreditCardField.NAME]) {
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
                isError = uiState.validation[CreditCardField.NAME] is Validation.Error,
                supportingText = when (val validation = uiState.validation[CreditCardField.NAME]) {
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
                state = closingDay,
                labelPosition = TextFieldLabelPosition.Attached(
                    alwaysMinimize = uiState.form.closingDayCalc != null
                ),
                label = {
                    Text(text = "Dia de Fechamento")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                placeholder = uiState.form.closingDayCalc?.let { hint ->
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
                state = dueDay,
                labelPosition = TextFieldLabelPosition.Attached(
                    alwaysMinimize = uiState.form.dueDayCalc != null
                ),
                label = {
                    Text(text = "Dia de Vencimento")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                placeholder = uiState.form.dueDayCalc?.let { hint ->
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
                    viewModel.onAction(CreditCardFormAction.Submit)
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
