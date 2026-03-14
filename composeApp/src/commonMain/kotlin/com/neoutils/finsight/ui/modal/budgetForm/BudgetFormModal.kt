@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.budgetForm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_form_edit_title
import com.neoutils.finsight.resources.budget_form_icon_helper
import com.neoutils.finsight.resources.budget_form_icon_label
import com.neoutils.finsight.resources.budget_form_icon_modal_title
import com.neoutils.finsight.resources.budget_form_limit_label
import com.neoutils.finsight.resources.budget_form_limit_type_fixed
import com.neoutils.finsight.resources.budget_form_limit_type_percentage
import com.neoutils.finsight.resources.budget_form_new_title
import com.neoutils.finsight.resources.budget_form_percentage_label
import com.neoutils.finsight.resources.budget_form_recurring_income_label
import com.neoutils.finsight.resources.budget_form_save
import com.neoutils.finsight.resources.budget_form_title_label
import com.neoutils.finsight.ui.component.IconPickerSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.MultiCategorySelector
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.util.FeatureIconCatalog
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class BudgetFormModal(
    private val budget: Budget? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val formatter = LocalCurrencyFormatter.current
        val viewModel = koinViewModel<BudgetFormViewModel> { parametersOf(budget) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.budget_form_icon_modal_title)

        val amount = rememberTextFieldState(budget?.amount?.let { formatter.format(it) } ?: "")

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }.collect {
                viewModel.onAction(BudgetFormAction.AmountChanged(it))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (uiState.isEditMode) {
                    stringResource(Res.string.budget_form_edit_title)
                } else {
                    stringResource(Res.string.budget_form_new_title)
                },
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onAction(BudgetFormAction.TitleChanged(it)) },
                label = { Text(text = stringResource(Res.string.budget_form_title_label)) },
                trailingIcon = when (uiState.validation[BudgetField.TITLE]) {
                    Validation.Validating -> {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }

                    else -> null
                },
                isError = uiState.validation[BudgetField.TITLE] is Validation.Error,
                supportingText = when (val validation = uiState.validation[BudgetField.TITLE]) {
                    is Validation.Error -> {
                        {
                            Text(text = stringUiText(validation.error))
                        }
                    }

                    else -> null
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MultiCategorySelector(
                selectedCategories = uiState.selectedCategories,
                categories = uiState.availableCategories,
                onCategoryToggled = { viewModel.onAction(BudgetFormAction.CategoryToggled(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            val limitTypeToggle: @Composable () -> Unit = {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp).padding(end = 12.dp)) {
                    SegmentedButton(
                        selected = uiState.limitType == LimitType.FIXED,
                        onClick = { viewModel.onAction(BudgetFormAction.LimitTypeChanged(LimitType.FIXED)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {},
                    ) {
                        Text(
                            text = stringResource(Res.string.budget_form_limit_type_fixed),
                            fontSize = 12.sp,
                        )
                    }
                    SegmentedButton(
                        selected = uiState.limitType == LimitType.PERCENTAGE,
                        onClick = { viewModel.onAction(BudgetFormAction.LimitTypeChanged(LimitType.PERCENTAGE)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {},
                    ) {
                        Text(
                            text = stringResource(Res.string.budget_form_limit_type_percentage),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            when (uiState.limitType) {
                LimitType.FIXED -> OutlinedTextField(
                    state = amount,
                    label = { Text(text = stringResource(Res.string.budget_form_limit_label)) },
                    trailingIcon = limitTypeToggle,
                    inputTransformation = rememberMoneyInputTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                LimitType.PERCENTAGE -> OutlinedTextField(
                    value = uiState.percentage,
                    onValueChange = { viewModel.onAction(BudgetFormAction.PercentageChanged(it)) },
                    label = { Text(text = stringResource(Res.string.budget_form_percentage_label)) },
                    trailingIcon = limitTypeToggle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = uiState.limitType == LimitType.PERCENTAGE) {
                RecurringIncomeSelector(
                    recurrings = uiState.incomeRecurrings,
                    selected = uiState.selectedRecurring,
                    onSelected = { viewModel.onAction(BudgetFormAction.RecurringSelected(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            IconPickerSelector(
                selectedIcon = uiState.selectedIcon,
                accentColor = accentColor,
                title = stringResource(Res.string.budget_form_icon_label),
                helperText = stringResource(Res.string.budget_form_icon_helper),
                onClick = {
                    modalManager.show(
                        IconPickerModal(
                            title = iconModalTitle,
                            selectedIcon = uiState.selectedIcon,
                            accentColor = accentColor,
                            icons = FeatureIconCatalog.withGeneral(
                                featureIcons = FeatureIconCatalog.budgets,
                                selectedIcon = uiState.selectedIcon,
                            ),
                            onIconSelected = { icon ->
                                viewModel.onAction(BudgetFormAction.IconSelected(icon))
                            },
                        )
                    )
                },
            )

            HorizontalDivider()

            Button(
                onClick = { viewModel.onAction(BudgetFormAction.Submit) },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.budget_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RecurringIncomeSelector(
    recurrings: List<Recurring>,
    selected: Recurring?,
    onSelected: (Recurring) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (recurrings.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(Res.string.budget_form_recurring_income_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = recurrings.isNotEmpty(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            recurrings.forEach { recurring ->
                DropdownMenuItem(
                    text = { Text(text = recurring.label, fontSize = 14.sp) },
                    onClick = {
                        onSelected(recurring)
                        expanded = false
                    },
                )
            }
        }
    }
}
