@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class DashboardUiState(
    val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val components: List<DashboardComponent> = emptyList(),
)

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)
