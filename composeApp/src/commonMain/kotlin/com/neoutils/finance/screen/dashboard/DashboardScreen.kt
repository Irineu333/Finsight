package com.neoutils.finance.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.component.BalanceCard
import com.neoutils.finance.component.BalanceCardConfig
import com.neoutils.finance.component.MonthSelector
import com.neoutils.finance.component.TransactionCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit = {},
    onSeeAllTransactions: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        },
        topBar = {
            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
                onPreviousMonth = viewModel::selectPreviousMonth,
                onNextMonth = viewModel::selectNextMonth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {

            item {
                BalanceCard(
                    balance = uiState.balance.balance,
                    modifier = Modifier.fillMaxWidth() ,
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
                        text = "Transações",
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
                        .animateItem()
                )
            }
        }
    }
}