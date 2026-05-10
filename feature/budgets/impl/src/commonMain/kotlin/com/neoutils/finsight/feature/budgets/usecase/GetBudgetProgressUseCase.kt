package com.neoutils.finsight.feature.budgets.usecase

import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.budgets.model.Budget
import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.feature.budgets.model.LimitType
import com.neoutils.finsight.feature.budgets.repository.IBudgetRepository
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class GetBudgetProgressUseCase(
    private val budgetRepository: IBudgetRepository,
    private val transactionRepository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val recurringRepository: IRecurringRepository,
) : IGetBudgetProgressUseCase {

    override suspend fun invoke(budgetId: Long, today: LocalDate): BudgetProgress? {
        val budget = budgetRepository.getBudgetById(budgetId) ?: return null
        val recurring = resolveRecurring(budget)
        val limit = resolveLimit(budget, recurring, today)
        val spent = resolveSpent(budget, today)

        return BudgetProgress(
            budget = budget.copy(amount = limit),
            spent = spent,
            recurringLabel = recurring?.label,
            recurring = recurring,
        )
    }

    override suspend fun invoke(
        budget: Budget,
        today: LocalDate
    ): BudgetProgress {
        val recurring = resolveRecurring(budget)
        val limit = resolveLimit(budget, recurring, today)
        val spent = resolveSpent(budget, today)

        return BudgetProgress(
            budget = budget.copy(amount = limit),
            spent = spent,
            recurringLabel = recurring?.label,
            recurring = recurring,
        )
    }

    private suspend fun resolveRecurring(budget: Budget): Recurring? {
        if (budget.limitType != LimitType.PERCENTAGE) return null
        val id = budget.recurringId ?: return null
        return recurringRepository.getRecurringById(id)
    }

    private suspend fun resolveLimit(
        budget: Budget,
        recurring: Recurring?,
        today: LocalDate,
    ): Double {
        if (budget.limitType == LimitType.FIXED) return budget.amount
        val percentage = budget.percentage ?: return 0.0
        val baseAmount = budget.recurringId
            ?.let { findConfirmedAmount(recurringId = it, today = today) }
            ?: recurring?.amount
            ?: 0.0
        return baseAmount * percentage / 100.0
    }

    private suspend fun findConfirmedAmount(recurringId: Long, today: LocalDate): Double? {
        return operationRepository.getAllOperations()
            .firstOrNull { it.recurring?.id == recurringId && it.date.yearMonth == today.yearMonth }
            ?.amount
    }

    private suspend fun resolveSpent(budget: Budget, today: LocalDate): Double {
        if (budget.categoryIds.isEmpty()) return 0.0
        val ym = today.yearMonth
        return transactionRepository
            .getTransactionsByCategoryIdsAndDateRange(
                categoryIds = budget.categoryIds,
                startDate = ym.safeOnDay(1),
                endDate = ym.safeOnDay(Int.MAX_VALUE),
            )
            .filter { it.type.isExpense }
            .sumOf { it.amount }
    }
}
