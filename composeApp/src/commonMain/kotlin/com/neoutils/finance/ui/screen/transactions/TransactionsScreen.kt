@file:OptIn(
    FormatStringsInDatetimeFormats::class,
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.neoutils.finance.ui.screen.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.component.CreditCardTotalSummaryCard
import com.neoutils.finance.ui.component.InvoiceSummaryCard
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.MonthSelector
import com.neoutils.finance.ui.component.SummaryCard
import com.neoutils.finance.ui.component.TransactionCard
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finance.ui.theme.Expense as ExpenseColor
import com.neoutils.finance.ui.theme.Income as IncomeColor
import com.neoutils.finance.ui.theme.InvoicePayment as BillPaymentColor
import com.neoutils.finance.util.DateFormats
import kotlin.time.ExperimentalTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val formats = DateFormats()

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

    val pageCount = if (uiState.creditCardOverview.hasData) 2 else 1
    val pagerState = rememberPagerState(pageCount = { pageCount })

    var invoicesExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
                onPreviousMonth = { onAction(TransactionsAction.PreviousMonth) },
                onNextMonth = { onAction(TransactionsAction.NextMonth) },
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
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 8.dp
                    ) { page ->
                        when (page) {
                            0 -> {
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
                                                targetMonth = uiState
                                                    .selectedYearMonth
                                                    .takeUnless {
                                                        uiState.isCurrentMonth
                                                    },
                                            )
                                        )
                                    }.takeUnless { uiState.isFutureMonth },
                                    onEditInitialBalance = {
                                        modalManager.show(
                                            EditBalanceModal(
                                                currentBalance = uiState.balanceOverview.initialBalance,
                                                type = EditBalanceModal.Type.INITIAL,
                                                targetMonth = uiState
                                                    .selectedYearMonth
                                                    .takeUnless {
                                                        uiState.isCurrentMonth
                                                    },
                                            )
                                        )
                                    }.takeUnless { uiState.isFutureMonth },
                                )
                            }

                            1 -> {
                                CreditCardTotalSummaryCard(
                                    overview = uiState.creditCardOverview,
                                    modifier = Modifier.fillMaxWidth(),
                                    isExpanded = invoicesExpanded,
                                    onExpandClick = { invoicesExpanded = !invoicesExpanded }
                                )
                            }
                        }
                    }

                    if (pageCount > 1) {
                        PagerIndicator(
                            pagerState = pagerState,
                            pageCount = pageCount
                        )
                    }
                }
            }

            if (invoicesExpanded) {
                items(uiState.creditCardOverview.invoices) { invoiceOverview ->
                    InvoiceSummaryCard(
                        overview = invoiceOverview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateItem()
                    )
                }
            }

            item {
                FiltersRow(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .animateItem()
                )
            }

            uiState.transactions.forEach { (date, transactions) ->
                item {
                    Text(
                        text = formats.dayOfWeek.format(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 16.dp)
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
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (transaction.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(ViewAdjustmentModal(transaction))
                                }

                                else -> {
                                    modalManager.show(ViewTransactionModal(transaction))
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
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        item {
            Box {
                TargetFilterChip(
                    selectedTarget = uiState.selectedTarget,
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
        label = { Text(selectedCategory?.name ?: "Categoria") },
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

    val chipColor =
        when (selectedType) {
            Transaction.Type.INCOME -> IncomeColor
            Transaction.Type.EXPENSE -> ExpenseColor
            Transaction.Type.ADJUSTMENT -> AdjustmentColor
            Transaction.Type.INVOICE_PAYMENT,
            Transaction.Type.ADVANCE_PAYMENT -> BillPaymentColor

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
                    Transaction.Type.INVOICE_PAYMENT -> "Pagamento"
                    Transaction.Type.ADVANCE_PAYMENT -> "Antecipação"
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
        onDismissRequest = { expanded = false }) {
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
                        text = when (type) {
                            Transaction.Type.INCOME -> "Entrada"
                            Transaction.Type.EXPENSE -> "Despesa"
                            Transaction.Type.ADJUSTMENT -> "Ajuste"
                            Transaction.Type.INVOICE_PAYMENT -> "Pagamento"
                            Transaction.Type.ADVANCE_PAYMENT -> "Antecipação"
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
                    Transaction.Target.ACCOUNT -> "Conta"
                    Transaction.Target.CREDIT_CARD -> "Cartão"
                    Transaction.Target.INVOICE_PAYMENT -> "Conta"
                    null -> "Conta"
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
            text = { Text("Todas") },
            onClick = {
                onAction(TransactionsAction.SelectTarget(null))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text("Conta") },
            onClick = {
                onAction(TransactionsAction.SelectTarget(Transaction.Target.ACCOUNT))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text("Cartão de Crédito") },
            onClick = {
                onAction(TransactionsAction.SelectTarget(Transaction.Target.CREDIT_CARD))
                expanded = false
            }
        )
    }
}

@Composable
private fun PagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (pagerState.currentPage == index) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}
