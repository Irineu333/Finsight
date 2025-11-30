@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.neoutils.finance.ui.modal.EditBalanceModal
import com.neoutils.finance.ui.modal.ViewTransactionModal
import com.neoutils.finance.ui.component.TransactionCard
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.extension.MonthNamesPortuguese
import com.neoutils.finance.ui.modal.ViewAdjustmentModal
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.datetime.YearMonth
import org.koin.compose.viewmodel.koinViewModel

private val yearMonthFormat = YearMonth.Format {
    monthName(MonthNamesPortuguese)
    chars(" ")
    year()
}

@Composable
fun DashboardScreen(
    openTransactions: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current

    DashboardContent(
        uiState = uiState,
        onSeeAllTransactions = openTransactions,
        modalManager = modalManager,
        openEditBalance = {
            modalManager.show(
                EditBalanceModal(
                    currentBalance = uiState.balance.balance,
                    onConfirm = { targetBalance ->
                        viewModel.onAction(
                            DashboardAction.AdjustBalance(targetBalance)
                        )
                    }
                )
            )
        }
    )
}

@Composable
private fun DashboardContent(
    onSeeAllTransactions: () -> Unit,
    uiState: DashboardUiState,
    modalManager: ModalManager,
    openEditBalance: () -> Unit
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = yearMonthFormat.format(uiState.yearMonth))
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
                onEditClick = openEditBalance
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
                    config = BalanceCardConfig.Income
                )

                BalanceCard(
                    balance = uiState.balance.expense,
                    modifier = Modifier.weight(1f),
                    config = BalanceCardConfig.Expense
                )
            }
        }

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
                    onClick = onSeeAllTransactions
                ) {
                    Text(text = "Ver Tudo")
                }
            }
        }

        items(
            items = uiState.recents,
            key = { it.id },
        ) { transaction ->
            TransactionCard(
                transaction = transaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                onClick = {
                    when (transaction.type) {
                        TransactionEntry.Type.ADJUSTMENT -> {
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