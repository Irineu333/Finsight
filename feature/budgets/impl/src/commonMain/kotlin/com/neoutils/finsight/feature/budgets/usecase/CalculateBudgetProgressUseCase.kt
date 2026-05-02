package com.neoutils.finsight.feature.budgets.usecase

import com.neoutils.finsight.feature.budgets.model.Budget
import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.feature.budgets.model.LimitType
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

class CalculateBudgetProgressUseCase : ICalculateBudgetProgressUseCase {
    override operator fun invoke(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        recurringList: List<Recurring>,
        operations: List<Operation>,
        today: LocalDate,
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
