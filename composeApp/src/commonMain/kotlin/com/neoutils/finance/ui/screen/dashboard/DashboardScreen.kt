@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.ui.component.BalanceCard
import com.neoutils.finance.ui.component.BalanceCardConfig
import com.neoutils.finance.ui.component.CategorySpendingCard
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.component.TransactionCard
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.modal.ViewAdjustmentModal
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finance.util.DateFormats
import org.koin.compose.viewmodel.koinViewModel

private val formats = DateFormats()

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    openCategories: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current

    DashboardContent(
        uiState = uiState,
        openTransactions = openTransactions,
        onOpenCategories = openCategories,
        modalManager = modalManager,
        openEditBalance = {
            modalManager.show(
                EditBalanceModal(
                    currentBalance = uiState.balance.balance,
                )
            )
        },
        openEditCreditCardBill = {
            modalManager.show(
                EditBalanceModal(
                    type = EditBalanceModal.Type.CREDIT_CARD,
                    currentBalance = uiState.creditCardBill,
                )
            )
        }
    )
}

@Composable
private fun DashboardContent(
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    openEditBalance: () -> Unit,
    openEditCreditCardBill: () -> Unit,
    onOpenCategories: () -> Unit,
    uiState: DashboardUiState,
    modalManager: ModalManager
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = formats.yearMonth.format(uiState.yearMonth))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background,
            ),
        )
    }
) { paddingValues ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp,
        ),
    ) {

        item {
            BalanceCard(
                balance = uiState.balance.balance,
                modifier = Modifier.fillMaxWidth(),
                onEditClick = openEditBalance,
                onClick = { openTransactions(null, Transaction.Target.ACCOUNT) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BalanceCard(
                    balance = uiState.balance.income,
                    modifier = Modifier.weight(1f),
                    config = BalanceCardConfig.Income,
                    onClick = { openTransactions(Transaction.Type.INCOME, null) }
                )

                BalanceCard(
                    balance = uiState.balance.expense,
                    modifier = Modifier.weight(1f),
                    config = BalanceCardConfig.Expense,
                    onClick = { openTransactions(Transaction.Type.EXPENSE, null) }
                )
            }
        }

        if (uiState.creditCardBill > 0) {
            item {
                BalanceCard(
                    balance = uiState.creditCardBill,
                    config = BalanceCardConfig.CreditCard,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    onClick = { openTransactions(null, Transaction.Target.CREDIT_CARD) },
                    onEditClick = openEditCreditCardBill
                )
            }
        }

        if (uiState.categorySpending.isNotEmpty()) {
            item {
                CategorySpendingCard(
                    categorySpending = uiState.categorySpending,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .animateItem(),
                    onCategoryClick = { category ->
                        modalManager.show(
                            ViewCategoryModal(category)
                        )
                    }
                )
            }
        }

        if (uiState.recents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recentes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    TextButton(
                        onClick = { openTransactions(null, null) }
                    ) {
                        Text(text = "Ver Tudo")
                    }
                }
            }
        }

        items(
            items = uiState.recents,
            key = { it.id },
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

        item {
            Card(
                onClick = onOpenCategories,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainer,
                    contentColor = colorScheme.onSurface,
                ),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Categorias",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowForwardIos,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}