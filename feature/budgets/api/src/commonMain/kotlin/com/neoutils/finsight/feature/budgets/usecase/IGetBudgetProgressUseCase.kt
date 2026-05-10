package com.neoutils.finsight.feature.budgets.usecase

import com.neoutils.finsight.feature.budgets.model.Budget
import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface IGetBudgetProgressUseCase {
    suspend operator fun invoke(
        budgetId: Long,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): BudgetProgress?

    suspend operator fun invoke(
        budget: Budget,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): BudgetProgress
}
