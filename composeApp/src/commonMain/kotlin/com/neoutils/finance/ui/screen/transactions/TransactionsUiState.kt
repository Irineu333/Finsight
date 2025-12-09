@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TransactionsUiState(
    val transactions: Map<LocalDate, List<Transaction>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val selectedCategory: Category? = null,
    val categories: List<Category> = listOf(),
    val selectedType: Transaction.Type? = null,
    val selectedTarget: Transaction.Target? = null
) {
    val currentMonth = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date.yearMonth

    val isCurrentMonth = selectedYearMonth == currentMonth
    val isFutureMonth = selectedYearMonth > currentMonth

    data class BalanceOverview(
        val initialBalance: Double = 0.0,
        val income: Double = 0.0,
        val expense: Double = 0.0,
        val adjustment: Double = 0.0,
        val finalBalance: Double = 0.0
    )
}