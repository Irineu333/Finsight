package com.neoutils.finsight.ui.screen.goals

import com.neoutils.finsight.domain.model.GoalProgress
import kotlinx.datetime.YearMonth

sealed class GoalsUiState {

    abstract val selectedMonth: YearMonth

    data class Loading(
        override val selectedMonth: YearMonth,
    ) : GoalsUiState()

    data class Empty(
        override val selectedMonth: YearMonth,
    ) : GoalsUiState()

    data class Content(
        val goalProgress: List<GoalProgress>,
        override val selectedMonth: YearMonth,
    ) : GoalsUiState()
}