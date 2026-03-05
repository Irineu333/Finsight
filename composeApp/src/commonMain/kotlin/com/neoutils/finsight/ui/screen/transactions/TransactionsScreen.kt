@file:OptIn(
    FormatStringsInDatetimeFormats::class,
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
)

package com.neoutils.finsight.ui.screen.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MonthSelector
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.component.SummaryCard
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.Income as IncomeColor

@Composable
fun TransactionsScreen(
    categoryType: Transaction.Type? = null,
    target: Transaction.Target? = null,
    viewModel: TransactionsViewModel = koinViewModel {
        parametersOf(categoryType, null, target)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TransactionsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun TransactionsContent(
    uiState: TransactionsUiState,
    onAction: (TransactionsAction) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val dateFormats = LocalDateFormats.current

    Scaffold(
        topBar = {
            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
                onPreviousMonth = { onAction(TransactionsAction.PreviousMonth) },
                onNextMonth = { onAction(TransactionsAction.NextMonth) },
                onMonthSelected = { selected ->
                    onAction(TransactionsAction.SelectMonth(selected))
                },
                showPickerChevron = false,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
            )
        },
        contentWindowInsets = WindowInsets(),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = "summary_card"
            ) {
                SummaryCard(
                    balanceOverview = uiState.balanceOverview,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    isCurrentMonth = uiState.isCurrentMonth,
                )
            }

            item(
                key = "filters_row"
            ) {
                FiltersRow(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                )
            }

            uiState.operations.forEach { (date, operations) ->
                item(
                    key = "date_title_$date"
                ) {
                    Text(
                        text = dateFormats.formatRelativeDate(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 16.dp)
                            .animateItem()
                    )
                }

                items(
                    items = operations,
                    key = { it.id }
                ) { operation ->
                    OperationCard(
                        operation = operation,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (operation.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(ViewAdjustmentModal(operation))
                                }

                                else -> {
                                    modalManager.show(ViewOperationModal(operation))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltersRow(
    uiState: TransactionsUiState,
    onAction: (TransactionsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(
            key = "category_filter"
        ) {
            Box {
                CategoryFilterChip(
                    selectedCategory = uiState.selectedCategory,
                    categories = uiState.categories,
                    onAction = onAction
                )
            }
        }

        item(
            key = "type_filter"
        ) {
            Box {
                TypeFilterChip(
                    selectedType = uiState.selectedType,
                    onAction = onAction
                )
            }
        }

        item(
            key = "target_filter"
        ) {
            Box {
                TargetFilterChip(
                    selectedTarget = uiState.selectedTarget,
                    onAction = onAction
                )
            }
        }

        item(
            key = "recurring_filter"
        ) {
            Box {
                RecurringFilterChip(
                    enabled = uiState.showRecurringOnly,
                    onAction = onAction,
                )
            }
        }

        item(
            key = "installment_filter"
        ) {
            Box {
                InstallmentFilterChip(
                    enabled = uiState.showInstallmentOnly,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    selectedCategory: Category?,
    categories: List<Category>,
    onAction: (TransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        selectedCategory?.let { category ->
            when (category.type) {
                Category.Type.INCOME -> IncomeColor
                Category.Type.EXPENSE -> ExpenseColor
            }
        }

    FilterChip(
        selected = selectedCategory != null,
        onClick = { expanded = true },
        label = { Text(selectedCategory?.name ?: stringResource(Res.string.transactions_filter_category)) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_category_all)) },
            onClick = {
                onAction(TransactionsAction.SelectCategory(null))
                expanded = false
            }
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(TransactionsAction.SelectCategory(category))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    selectedType: Transaction.Type?,
    onAction: (TransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        when (selectedType) {
            Transaction.Type.INCOME -> IncomeColor
            Transaction.Type.EXPENSE -> ExpenseColor
            Transaction.Type.ADJUSTMENT -> AdjustmentColor

            null -> null
        }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    Transaction.Type.INCOME -> stringResource(Res.string.transactions_filter_type_income)
                    Transaction.Type.EXPENSE -> stringResource(Res.string.transactions_filter_type_expense)
                    Transaction.Type.ADJUSTMENT -> stringResource(Res.string.transactions_filter_type_adjustment)
                    null -> stringResource(Res.string.transactions_filter_type)
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_type_all)) },
            onClick = {
                onAction(TransactionsAction.SelectType(null))
                expanded = false
            }
        )

        Transaction.Type.entries.forEach { type ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = when (type) {
                            Transaction.Type.INCOME -> stringResource(Res.string.transactions_filter_type_income)
                            Transaction.Type.EXPENSE -> stringResource(Res.string.transactions_filter_type_expense)
                            Transaction.Type.ADJUSTMENT -> stringResource(Res.string.transactions_filter_type_adjustment)
                        }
                    )
                },
                onClick = {
                    onAction(TransactionsAction.SelectType(type))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun RecurringFilterChip(
    enabled: Boolean,
    onAction: (TransactionsAction) -> Unit,
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(TransactionsAction.ToggleRecurring(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_recurring))
        },
    )
}

@Composable
private fun InstallmentFilterChip(
    enabled: Boolean,
    onAction: (TransactionsAction) -> Unit,
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(TransactionsAction.ToggleInstallment(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_installment))
        },
    )
}

@Composable
private fun TargetFilterChip(
    selectedTarget: Transaction.Target?,
    onAction: (TransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    FilterChip(
        selected = selectedTarget != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedTarget) {
                    Transaction.Target.ACCOUNT -> stringResource(Res.string.transactions_filter_account)
                    Transaction.Target.CREDIT_CARD -> stringResource(Res.string.transactions_filter_credit_card)
                    null -> stringResource(Res.string.transactions_filter_account)
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_category_all)) },
            onClick = {
                onAction(TransactionsAction.SelectTarget(null))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_account)) },
            onClick = {
                onAction(TransactionsAction.SelectTarget(Transaction.Target.ACCOUNT))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_credit_card_label)) },
            onClick = {
                onAction(TransactionsAction.SelectTarget(Transaction.Target.CREDIT_CARD))
                expanded = false
            }
        )
    }
}
