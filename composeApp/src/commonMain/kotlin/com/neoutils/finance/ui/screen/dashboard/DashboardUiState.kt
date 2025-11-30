@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import com.neoutils.finance.data.Category
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class DashboardUiState(
    val recents: List<TransactionEntry> = emptyList(),
    val balance: BalanceStats = BalanceStats(),
    val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val categories: Map<Long, Category> = emptyMap()
) {
    data class BalanceStats(
        val income: Double = 0.0,
        val expense: Double = 0.0,
        val balance: Double = 0.0
    )
}