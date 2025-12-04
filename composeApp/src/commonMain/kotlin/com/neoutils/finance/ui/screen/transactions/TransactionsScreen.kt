@file:OptIn(FormatStringsInDatetimeFormats::class, ExperimentalTime::class, ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.screen.transactions

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
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.modal.EditBalanceModal
import com.neoutils.finance.ui.modal.ViewAdjustmentModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.component.MonthSelector
import com.neoutils.finance.ui.component.SummaryCard
import com.neoutils.finance.ui.component.TransactionCard
import com.neoutils.finance.ui.theme.Expense as ExpenseColor
import com.neoutils.finance.ui.theme.Income as IncomeColor
import com.neoutils.finance.ui.theme.Adjustment as AdjustmentColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime

private val DayOfWeekNamesPortuguese = DayOfWeekNames(
    sunday = "Domingo",
    monday = "Segunda-feira",
    tuesday = "Terça-feira",
    wednesday = "Quarta-feira",
    thursday = "Quinta-feira",
    friday = "Sexta-feira",
    saturday = "Sábado"
)

private val sectionDateFormat = LocalDate.Format {
    day()
    chars(", ")
    dayOfWeek(DayOfWeekNamesPortuguese)
}

@Composable
fun TransactionsScreen(
    categoryType: Transaction.Type? = null,
    viewModel: TransactionsViewModel = koinViewModel { parametersOf(categoryType) },
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

    Scaffold(
        topBar = {
            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
                onPreviousMonth = {
                    onAction(TransactionsAction.PreviousMonth)
                },
                onNextMonth = {
                    onAction(TransactionsAction.NextMonth)
                },
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            item {
                SummaryCard(
                    balanceOverview = uiState.balanceOverview,
                    modifier = Modifier.fillMaxWidth(),
                    isCurrentMonth = uiState.isCurrentMonth,
                    onEditBalance = {
                        modalManager.show(
                            EditBalanceModal(
                                currentBalance = uiState.balanceOverview.finalBalance,
                                type = if (uiState.isCurrentMonth) {
                                    EditBalanceModal.Type.CURRENT
                                } else {
                                    EditBalanceModal.Type.FINAL
                                },
                                targetMonth = uiState.selectedYearMonth.takeUnless { uiState.isCurrentMonth },
                                onConfirm = {
                                    onAction(
                                        TransactionsAction.AdjustBalance(it)
                                    )
                                }
                            )
                        )
                    }.takeUnless {
                        uiState.isFutureMonth
                    },
                    onEditInitialBalance = {
                        modalManager.show(
                            EditBalanceModal(
                                currentBalance = uiState.balanceOverview.initialBalance,
                                type =  EditBalanceModal.Type.INITIAL,
                                targetMonth = uiState.selectedYearMonth.takeUnless { uiState.isCurrentMonth },
                                onConfirm = {
                                    onAction(
                                        TransactionsAction.AdjustInitialBalance(it)
                                    )
                                }
                            )
                        )
                    }.takeUnless {
                        uiState.isFutureMonth
                    }
                )
            }

            item {
                FiltersRow(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.transactions.forEach { (date, transactions) ->
                item {
                    Text(
                        text = sectionDateFormat.format(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .animateItem()
                    )
                }

                items(
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        category = transaction.category,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (transaction.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(
                                        ViewAdjustmentModal(transaction)
                                    )
                                }

                                else -> {
                                    modalManager.show(
                                        ViewTransactionModal(transaction)
                                    )
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
    LazyRow (
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Box {
                CategoryFilterChip(
                    selectedCategory = uiState.selectedCategory,
                    categories = uiState.categories,
                    onAction = onAction
                )
            }
        }

        item {
            Box {
                TypeFilterChip(
                    selectedType = uiState.selectedType,
                    onAction = onAction
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

    val chipColor = selectedCategory?.let { category ->
        when (category.type) {
            Category.CategoryType.INCOME -> IncomeColor
            Category.CategoryType.EXPENSE -> ExpenseColor
        }
    }

    FilterChip(
        selected = selectedCategory != null,
        onClick = { expanded = true },
        label = {
            Text(selectedCategory?.name ?: "Categoria")
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
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Todas") },
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

    val chipColor = when (selectedType) {
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
                    Transaction.Type.INCOME -> "Entrada"
                    Transaction.Type.EXPENSE -> "Despesa"
                    Transaction.Type.ADJUSTMENT -> "Ajuste"
                    null -> "Tipo"
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
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Todos") },
            onClick = {
                onAction(TransactionsAction.SelectType(null))
                expanded = false
            }
        )

        Transaction.Type.entries.forEach { type ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (type) {
                            Transaction.Type.INCOME -> "Entrada"
                            Transaction.Type.EXPENSE -> "Despesa"
                            Transaction.Type.ADJUSTMENT -> "Ajuste"
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
