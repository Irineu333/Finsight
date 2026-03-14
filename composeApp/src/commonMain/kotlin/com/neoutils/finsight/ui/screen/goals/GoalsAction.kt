package com.neoutils.finsight.ui.screen.goals

import kotlinx.datetime.YearMonth

sealed class GoalsAction {
    data class SelectMonth(val yearMonth: YearMonth) : GoalsAction()
}
