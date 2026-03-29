@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed class DashboardUiState {
    abstract val yearMonth: YearMonth

    data class Loading(
        override val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    ) : DashboardUiState()

    data class Viewing(
        override val yearMonth: YearMonth,
        val components: List<DashboardComponent>,
    ) : DashboardUiState()

    data class Editing(
        override val yearMonth: YearMonth,
        val items: List<DashboardEditItem>,
        val availableItems: List<DashboardEditItem>,
    ) : DashboardUiState()
}

data class DashboardEditItem(
    val key: String,
    val title: UiText,
    val preview: DashboardComponent,
    val config: Map<String, String> = emptyMap(),
)

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)