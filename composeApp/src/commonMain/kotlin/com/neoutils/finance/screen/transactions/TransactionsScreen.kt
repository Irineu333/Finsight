@file:OptIn(FormatStringsInDatetimeFormats::class, ExperimentalTime::class, ExperimentalMaterial3Api::class)

package com.neoutils.finance.screen.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.component.*
import com.neoutils.finance.extension.MonthNamesPortuguese
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.modal.EditBalanceModal
import com.neoutils.finance.modal.ViewTransactionModal
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    TransactionsContent(
        uiState = uiState,
        contentPadding = contentPadding,
        selectPreviousMonth = viewModel::selectPreviousMonth,
        selectNextMonth = viewModel::selectNextMonth,
        onAdjustBalance = viewModel::adjustBalance,
        onAdjustInitialBalance = viewModel::adjustInitialBalance,
    )
}

@Composable
private fun TransactionsContent(
    selectPreviousMonth: () -> Unit = {},
    selectNextMonth: () -> Unit = {},
    contentPadding: PaddingValues,
    uiState: TransactionsUiState,
    onAdjustBalance: (Double) -> Unit = {},
    onAdjustInitialBalance: (Double) -> Unit = {},
) {
    val modalManager = LocalModalManager.current

    Scaffold(
        topBar = {
            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
                onPreviousMonth = selectPreviousMonth,
                onNextMonth = selectNextMonth,
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
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            item {
                SummaryCard(
                    balanceOverview = uiState.balanceOverview,
                    modifier = Modifier.fillMaxWidth(),
                    onEditBalance = {
                        modalManager.show(
                            EditBalanceModal(
                                currentBalance = uiState.balanceOverview.finalBalance,
                                onConfirm = onAdjustBalance
                            )
                        )
                    }.takeUnless {
                        uiState.isFutureMonth
                    },
                    onEditInitialBalance = {
                        modalManager.show(
                            EditBalanceModal(
                                currentBalance = uiState.balanceOverview.initialBalance,
                                onConfirm = onAdjustInitialBalance
                            )
                        )
                    }.takeUnless {
                        uiState.isFutureMonth
                    }
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
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            when (transaction.type) {
                                com.neoutils.finance.data.TransactionEntry.Type.ADJUSTMENT -> {
                                    modalManager.show(
                                        com.neoutils.finance.modal.ViewAdjustmentModal(transaction)
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
