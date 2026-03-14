package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
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
        recurringList: List<Recurring> = emptyList(),
        operations: List<Operation> = emptyList(),
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): List<BudgetProgress> {
        return budgets.map { budget ->
            val limit = when (budget.limitType) {
                LimitType.FIXED -> budget.amount
                LimitType.PERCENTAGE -> {
                    val confirmedAmount = operations
                        .filter { it.recurring?.id == budget.recurringId }
                        .filter { it.date.yearMonth == today.yearMonth }
                        .firstOrNull()
                        ?.amount
                    val fallbackAmount = recurringList.find { it.id == budget.recurringId }?.amount ?: 0.0
                    (confirmedAmount ?: fallbackAmount) * (budget.percentage ?: 0.0) / 100.0
                }
            }
            val spent = transactions
                .filter { tx -> tx.type.isExpense && budget.categories.any { it.id == tx.category?.id } }
                .filter { it.date.yearMonth == today.yearMonth }
                .sumOf { it.amount }
            val recurring = if (budget.limitType == LimitType.PERCENTAGE) {
                recurringList.find { it.id == budget.recurringId }
            } else null
            BudgetProgress(
                budget = budget.copy(amount = limit),
                spent = spent,
                recurringLabel = recurring?.label,
                recurring = recurring,
            )
        }
    }
}
