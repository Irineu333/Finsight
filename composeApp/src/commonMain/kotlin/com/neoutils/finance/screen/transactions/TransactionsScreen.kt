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
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.extension.MonthNamesPortuguese
import kotlinx.datetime.YearMonth
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.modal.BalanceEditType
import com.neoutils.finance.modal.EditBalanceModal
import com.neoutils.finance.modal.ViewAdjustmentModal
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
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                                type = if (uiState.isCurrentMonth) BalanceEditType.CURRENT else BalanceEditType.FINAL,
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
                                type = BalanceEditType.INITIAL,
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
    }
}
