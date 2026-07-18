package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

class CalculateBudgetProgressUseCase {
    /**
     * [categoryBalances] maps a category's chart-account id to its `Σ entries` in the
     * selected month (debit-positive, so an EXPENSE account already reads as +spent).
     * The caller reads it from the ledger — this use case lives in the feature `api`
     * and MUST NOT depend on another feature's repository (star topology), so the
     * ledger read happens in the `impl` that owns the `IEntryRepository` dependency.
     */
    operator fun invoke(
        budgets: List<Budget>,
        categoryBalances: Map<Long, Double>,
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
            val spent = budget.categories
                .filter { it.type.isExpense }
                .sumOf { category -> category.accountId?.let { categoryBalances[it] } ?: 0.0 }
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
