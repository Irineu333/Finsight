package com.neoutils.finsight.ui.modal.creditCardForm

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_form_closing_day_label
import com.neoutils.finsight.resources.credit_card_form_due_day_label
import com.neoutils.finsight.resources.credit_card_form_edit_title
import com.neoutils.finsight.resources.credit_card_form_icon_helper
import com.neoutils.finsight.resources.credit_card_form_icon_label
import com.neoutils.finsight.resources.credit_card_form_icon_modal_title
import com.neoutils.finsight.resources.credit_card_form_limit_label
import com.neoutils.finsight.resources.credit_card_form_name_label
import com.neoutils.finsight.resources.credit_card_form_new_title
import com.neoutils.finsight.resources.credit_card_form_save
import com.neoutils.finsight.ui.component.IconPickerSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.util.DayInputTransformation
import com.neoutils.finsight.util.FeatureIconCatalog
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.util.stringUiText
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CreditCardFormModal(
    private val creditCard: CreditCard? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CreditCardFormViewModel> { parametersOf(creditCard) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.credit_card_form_icon_modal_title)

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (uiState.isEditMode) stringResource(Res.string.credit_card_form_edit_title) else stringResource(Res.string.credit_card_form_new_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = name,
                label = { Text(text = stringResource(Res.string.credit_card_form_name_label)) },
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = limit,
                label = { Text(text = stringResource(Res.string.credit_card_form_limit_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                inputTransformation = rememberMoneyInputTransformation(),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = closingDay,
                labelPosition = TextFieldLabelPosition.Attached(
                    alwaysMinimize = uiState.form.closingDayCalc != null
                ),
                label = {
                    Text(text = stringResource(Res.string.credit_card_form_closing_day_label))
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = dueDay,
                labelPosition = TextFieldLabelPosition.Attached(
                    alwaysMinimize = uiState.form.dueDayCalc != null
                ),
                label = {
                    Text(text = stringResource(Res.string.credit_card_form_due_day_label))
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

            Spacer(modifier = Modifier.height(8.dp))

            IconPickerSelector(
                selectedIcon = uiState.selectedIcon,
                accentColor = accentColor,
                title = stringResource(Res.string.credit_card_form_icon_label),
                helperText = stringResource(Res.string.credit_card_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = uiState.selectedIcon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.creditCards,
                                selectedIcon = uiState.selectedIcon,
                            ),
                            onIconSelected = { icon ->
                                viewModel.onAction(CreditCardFormAction.IconSelected(icon))
                            },
                        )
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Button(
                onClick = {
                    viewModel.onAction(CreditCardFormAction.Submit)
                },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.credit_card_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
