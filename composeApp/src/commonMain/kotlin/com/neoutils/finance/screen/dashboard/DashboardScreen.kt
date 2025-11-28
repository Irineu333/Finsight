@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.screen.dashboard

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
import com.neoutils.finance.component.BalanceCard
import com.neoutils.finance.component.BalanceCardConfig
import com.neoutils.finance.component.TransactionCard
import com.neoutils.finance.extension.MonthNamesPortuguese
import kotlinx.datetime.YearMonth
import org.koin.compose.viewmodel.koinViewModel

private val yearMonthFormat = YearMonth.Format {
    monthName(MonthNamesPortuguese)
    chars(" ")
    year()
}

@Composable
fun DashboardScreen(
    onSeeAllTransactions: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onSeeAllTransactions = onSeeAllTransactions,
        contentPadding = contentPadding
    )
}

@Composable
private fun DashboardContent(
    onSeeAllTransactions: () -> Unit,
    contentPadding: PaddingValues,
    uiState: DashboardUiState
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = yearMonthFormat.format(uiState.currentMonth))
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
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {

        item {
            BalanceCard(
                balance = uiState.balance.balance,
                modifier = Modifier.fillMaxWidth(),
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