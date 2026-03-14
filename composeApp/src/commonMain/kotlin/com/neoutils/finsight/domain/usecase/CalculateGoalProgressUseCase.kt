package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Goal
import com.neoutils.finsight.domain.model.GoalProgress
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

class CalculateGoalProgressUseCase {
    operator fun invoke(
        goals: List<Goal>,
        transactions: List<Transaction>,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): List<GoalProgress> {
        return goals.map { goal ->
            val earned = transactions
                .filter { tx -> tx.type.isIncome && goal.categories.any { it.id == tx.category?.id } }
                .filter { it.date.yearMonth == today.yearMonth }
                .sumOf { it.amount }
            GoalProgress(goal = goal, earned = earned)
        }
    }
}
