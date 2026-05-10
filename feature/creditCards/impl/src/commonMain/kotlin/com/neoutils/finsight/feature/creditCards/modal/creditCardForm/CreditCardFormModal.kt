package com.neoutils.finsight.feature.creditCards.modal.creditCardForm

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import com.neoutils.finsight.feature.creditCards.resources.Res
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_closing_day_label
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_due_day_label
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_edit_title
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_icon_helper
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_icon_label
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_icon_modal_title
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_limit_label
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_name_label
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_new_title
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_save
import com.neoutils.finsight.core.ui.component.IconPickerSelector
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.component.ModalErrorContent
import com.neoutils.finsight.feature.creditCards.resources.credit_card_form_unavailable
import com.neoutils.finsight.core.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.DayInputTransformation
import com.neoutils.finsight.core.ui.util.FeatureIconCatalog
import com.neoutils.finsight.core.ui.util.Validation
import com.neoutils.finsight.core.ui.util.rememberMoneyInputTransformation
import com.neoutils.finsight.core.ui.util.stringUiText
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CreditCardFormModal(
    private val creditCardId: Long? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<CreditCardFormViewModel> {
            parametersOf(creditCardId)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        when (val state = uiState) {
            CreditCardFormUiState.Loading -> LoadingContent()
            CreditCardFormUiState.Error -> ErrorContent()
            is CreditCardFormUiState.Content -> Content(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }

    @Composable
    private fun ErrorContent() {
        val manager = LocalModalManager.current
        ModalErrorContent(
            message = stringResource(Res.string.credit_card_form_unavailable),
            onClose = { manager.dismiss() },
        )
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.credit_card_form_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(96.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    @Composable
    private fun Content(
        state: CreditCardFormUiState.Content,
        onAction: (CreditCardFormAction) -> Unit,
    ) {
        val modalManager = LocalModalManager.current
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.credit_card_form_icon_modal_title)

        val name = rememberTextFieldState(state.form.name)
        val limit = rememberTextFieldState(state.form.limit)
        val closingDay = rememberTextFieldState(state.form.closingDayUser)
        val dueDay = rememberTextFieldState(state.form.dueDayUser)

        LaunchedEffect(Unit) {
            snapshotFlow { name.text.toString() }
                .drop(1)
                .collect { onAction(CreditCardFormAction.NameChanged(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { limit.text.toString() }
                .drop(1)
                .collect { onAction(CreditCardFormAction.LimitChanged(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { closingDay.text.toString() }
                .drop(1)
                .collect { onAction(CreditCardFormAction.ClosingDayChanged(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { dueDay.text.toString() }
                .drop(1)
                .collect { onAction(CreditCardFormAction.DueDayChanged(it)) }
        }

        val selectedIcon = AppIcon.fromKey(state.form.iconKey)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.isEditMode) {
                    stringResource(Res.string.credit_card_form_edit_title)
                } else {
                    stringResource(Res.string.credit_card_form_new_title)
                },
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = name,
                label = { Text(text = stringResource(Res.string.credit_card_form_name_label)) },
                trailingIcon = when (state.validation[CreditCardField.NAME]) {
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
                isError = state.validation[CreditCardField.NAME] is Validation.Error,
                supportingText = when (val validation = state.validation[CreditCardField.NAME]) {
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
                    alwaysMinimize = state.form.closingDayCalc != null
                ),
                label = {
                    Text(text = stringResource(Res.string.credit_card_form_closing_day_label))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                placeholder = state.form.closingDayCalc?.let { hint ->
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
                    alwaysMinimize = state.form.dueDayCalc != null
                ),
                label = {
                    Text(text = stringResource(Res.string.credit_card_form_due_day_label))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                placeholder = state.form.dueDayCalc?.let { hint ->
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
                selectedIcon = selectedIcon,
                accentColor = accentColor,
                title = stringResource(Res.string.credit_card_form_icon_label),
                helperText = stringResource(Res.string.credit_card_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = selectedIcon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.creditCards,
                                selectedIcon = selectedIcon,
                            ),
                            onIconSelected = { icon ->
                                onAction(CreditCardFormAction.IconSelected(icon))
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
                    onAction(CreditCardFormAction.Submit)
                },
                enabled = state.canSubmit,
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
