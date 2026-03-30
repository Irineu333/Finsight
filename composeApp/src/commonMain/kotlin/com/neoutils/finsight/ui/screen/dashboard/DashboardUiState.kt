@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal const val EDIT_SECTION_HEADER_KEY = "section_header"
internal const val EDIT_AVAILABLE_PLACEHOLDER_KEY = "available_placeholder"

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
        val components: List<DashboardComponent>,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
        val configByKey: Map<String, Map<String, String>> = emptyMap(),
    ) : DashboardUiState()

    data class Editing(
        override val yearMonth: YearMonth,
        val items: List<DashboardEditItem>,
        val availableItems: List<DashboardEditItem>,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
    ) : DashboardUiState()
}

data class DashboardEditItem(
    val key: String,
    val title: UiText,
    val preview: DashboardComponentVariant,
    val config: Map<String, String> = emptyMap(),
)

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)
