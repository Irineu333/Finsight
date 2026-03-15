@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.budgetForm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.IconPickerSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.MultiCategorySelector
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
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

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            MultiCategorySelector(
                selectedCategories = uiState.selectedCategories,
                categories = uiState.availableCategories,
                onCategoryToggled = { viewModel.onAction(BudgetFormAction.CategoryToggled(it)) },
                onEmpty = { modalManager.show(CategoryFormModal()) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (uiState.limitType) {
                LimitType.FIXED -> {
                    OutlinedTextField(
                        state = amount,
                        label = { Text(text = stringResource(Res.string.budget_form_limit_label)) },
                        trailingIcon = {
                            LimitTypeToggle(
                                limitType = uiState.limitType,
                                onLimitTypeChanged = { viewModel.onAction(BudgetFormAction.LimitTypeChanged(it)) },
                            )
                        },
                        inputTransformation = rememberMoneyInputTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                LimitType.PERCENTAGE -> {
                    OutlinedTextField(
                        value = uiState.percentage,
                        onValueChange = { viewModel.onAction(BudgetFormAction.PercentageChanged(it)) },
                        label = { Text(text = stringResource(Res.string.budget_form_percentage_label)) },
                        trailingIcon = {
                            LimitTypeToggle(
                                limitType = uiState.limitType,
                                onLimitTypeChanged = { viewModel.onAction(BudgetFormAction.LimitTypeChanged(it)) },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AnimatedVisibility(visible = uiState.limitType == LimitType.PERCENTAGE) {
                RecurringIncomeSelector(
                    recurrings = uiState.incomeRecurrings,
                    selected = uiState.selectedRecurring,
                    onSelected = { viewModel.onAction(BudgetFormAction.RecurringSelected(it)) },
                    onEmpty = { modalManager.show(RecurringFormModal()) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp)
            )
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
private fun LimitTypeToggle(
    limitType: LimitType,
    onLimitTypeChanged: (LimitType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .height(32.dp)
            .padding(end = 12.dp)
    ) {
        SegmentedButton(
            selected = limitType == LimitType.FIXED,
            onClick = { onLimitTypeChanged(LimitType.FIXED) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {},
        ) {
            Text(
                text = stringResource(Res.string.budget_form_limit_type_fixed),
                fontSize = 12.sp,
            )
        }
        SegmentedButton(
            selected = limitType == LimitType.PERCENTAGE,
            onClick = { onLimitTypeChanged(LimitType.PERCENTAGE) },
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

@Composable
private fun RecurringIncomeSelector(
    recurrings: List<Recurring>,
    selected: Recurring?,
    onSelected: (Recurring) -> Unit,
    modifier: Modifier = Modifier,
    onEmpty: (() -> Unit)? = null,
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
                if (recurrings.isEmpty() && onEmpty != null) {
                    IconButton(onClick = onEmpty) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            enabled = recurrings.isNotEmpty() || onEmpty != null,
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
