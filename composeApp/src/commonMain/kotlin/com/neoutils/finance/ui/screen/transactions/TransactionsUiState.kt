@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.data.Category
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TransactionsUiState(
    val transactions: Map<LocalDate, List<TransactionEntry>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val categories: Map<Long, Category> = emptyMap(),
    val selectedCategoryId: Long? = null,
    val selectedType: TransactionEntry.Type? = null
) {
    val currentMonth = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date.yearMonth

    val isCurrentMonth = selectedYearMonth == currentMonth
    val isFutureMonth = selectedYearMonth > currentMonth

    val availableCategories: List<Category>
        get() = categories.values.toList()

    val hasActiveFilters: Boolean
        get() = selectedCategoryId != null || selectedType != null

    data class BalanceOverview(
        val initialBalance: Double = 0.0,
        val income: Double = 0.0,
        val expense: Double = 0.0,
        val adjustment: Double = 0.0,
        val finalBalance: Double = 0.0
    )
}