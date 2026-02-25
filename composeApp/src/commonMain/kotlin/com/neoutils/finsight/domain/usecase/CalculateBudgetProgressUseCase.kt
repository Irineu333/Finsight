package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

class CalculateBudgetProgressUseCase {
    operator fun invoke(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): List<BudgetProgress> {
        return budgets.map { budget ->
            val spent = transactions
                .filter { tx -> tx.type.isExpense && budget.categories.any { it.id == tx.category?.id } }
                .filter { it.date.yearMonth == today.yearMonth }
                .sumOf { it.amount }
            BudgetProgress(budget = budget, spent = spent)
        }
    }
}
