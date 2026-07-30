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
import androidx.compose.material.icons.filled.ChevronRight
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
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.MultiCategorySelector
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyPickerModal
import com.neoutils.finsight.ui.modal.iconPicker.IconPickerModal
import com.neoutils.finsight.extension.currencyDisplayName
import com.neoutils.finsight.extension.currencySymbol
import com.neoutils.finsight.util.FeatureIconCatalog
import com.neoutils.finsight.util.Validation
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
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
        val categoriesEntry = koinInject<CategoriesEntry>()
        val recurringEntry = koinInject<RecurringEntry>()
        val accentColor = MaterialTheme.colorScheme.primary
        val iconModalTitle = stringResource(Res.string.budget_form_icon_modal_title)
        val currencyModalTitle = stringResource(Res.string.budget_form_currency_modal_title)

        val amount = rememberTextFieldState(budget?.amount?.let { formatter.format(it, budget.currency) } ?: "")

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
                onEmpty = { modalManager.show(categoriesEntry.categoryFormModal()) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Only where there is a choice to make. With one currency among the accounts the
            // form is exactly the one it was before currencies existed — the budget form
            // chooses among the currencies that exist, and never creates one, which is what
            // separates it from the account form (design D13 against D23).
            if (uiState.offersCurrencyChoice || (uiState.isCurrencyLocked && uiState.offeredCurrencies.size > 1)) {
                CurrencyRow(
                    currency = uiState.currency,
                    isLocked = uiState.isCurrencyLocked,
                    onClick = {
                        modalManager.show(
                            CurrencyPickerModal(
                                title = currencyModalTitle,
                                currencies = uiState.offeredCurrencies,
                                selected = uiState.currency,
                                onCurrencySelected = { viewModel.onAction(BudgetFormAction.CurrencySelected(it)) },
                            )
                        )
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

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
                        inputTransformation = rememberMoneyInputTransformation(uiState.currency, amount),
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
                    onEmpty = { modalManager.show(recurringEntry.recurringFormModal()) },
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

/**
 * The limit's currency as a row of the form: the glyph, the code and the name, with the
 * chevron gone and the tone dropped to `onSurfaceVariant` when it is locked — the same
 * signifier the app already uses for "this cannot change", so a locked currency reads like a
 * default account that cannot be unset rather than like a control that failed.
 */
@Composable
private fun CurrencyRow(
    currency: String,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (isLocked) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        enabled = !isLocked,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    text = currencySymbol(currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.budget_form_currency_label),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$currency · ${currencyDisplayName(currency)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isLocked) {
                    Text(
                        text = stringResource(Res.string.budget_form_currency_locked),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!isLocked) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
