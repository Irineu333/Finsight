@file:OptIn(FormatStringsInDatetimeFormats::class, ExperimentalTime::class, ExperimentalMaterial3Api::class)

package com.neoutils.finance.screen.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.component.*
import com.neoutils.finance.manager.LocalModalManager
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

private val sectionDateFormat = LocalDate.Format {
    day()
    chars(" de ")
    monthName(MonthNamesPortuguese)
}

@Composable
fun TransactionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    TransactionsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun TransactionsContent(
    onNavigateBack: () -> Unit = {},
    uiState: TransactionsUiState,
) {
    val modalManager = LocalModalManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { modalManager.show(AddTransactionModal()) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            item {
                SummaryCard(
                    balanceOverview = uiState.balanceOverview,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.transactions.forEach { (date, transactions) ->
                item {
                    Text(
                        text = sectionDateFormat.format(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
