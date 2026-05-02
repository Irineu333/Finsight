@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.dashboard.screen

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.utils.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed class DashboardUiState {
    abstract val yearMonth: YearMonth

    data class Loading(
        override val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    ) : DashboardUiState()

    data class Empty(
        override val yearMonth: YearMonth,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
    ) : DashboardUiState()

    data class Viewing(
        override val yearMonth: YearMonth,
        val items: List<DashboardComponentVariant>,
        val showEditTip: Boolean = false,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
    ) : DashboardUiState()

    data class Editing(
        override val yearMonth: YearMonth,
        val activeItems: List<DashboardEditItem>,
        val availableItems: List<DashboardEditItem>,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
    ) : DashboardUiState()
}
