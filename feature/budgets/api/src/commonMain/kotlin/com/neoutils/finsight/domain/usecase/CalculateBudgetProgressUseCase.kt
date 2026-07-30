package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ASSUMED_SINGLE_CURRENCY
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.dimensionBalancesInMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

/**
 * How much of each budget has been spent, and out of how much.
 *
 * It reads the ledger itself. It used to be handed `categoryBalances` already
 * computed, because an `api` may not depend on another feature's repository — but
 * `IEntryRepository` is a *core* now, so the rule that forced the number to be
 * computed one layer up no longer applies, and three callers stop each gathering
 * the same map before asking the same question.
 */
class CalculateBudgetProgressUseCase(
    private val entryRepository: IEntryRepository,
) {
    /**
     * [month] is the month being looked at: a `PERCENTAGE` limit is based on the
     * recurring confirmed *in that month*, not in the current one, so browsing a
     * past month must not consult today's confirmation.
     */
    suspend operator fun invoke(
        budgets: List<Budget>,
        recurringList: List<Recurring> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        month: YearMonth = Clock.System.todayIn(TimeZone.currentSystemDefault()).yearMonth,
    ): List<BudgetProgress> {
        // Σ entries carrying each budgeted category's dimension, in the month —
        // debit-positive, so an expense already reads as +spent.
        val categoryBalances = entryRepository.dimensionBalancesInMonth(
            month = month,
            dimensionIds = budgets.flatMap { budget -> budget.categories.map { it.dimensionId } },
        )
        return budgets.map { budget ->
            val limit = when (budget.limitType) {
                LimitType.FIXED -> budget.amount
                LimitType.PERCENTAGE -> {
                    val confirmedAmount = transactions
                        .filter { it.recurringId == budget.recurringId }
                        .filter { it.date.yearMonth == month }
                        .firstOrNull()
                        ?.amount
                    val fallbackAmount = recurringList.find { it.id == budget.recurringId }?.amount ?: 0.0
                    (confirmedAmount ?: fallbackAmount) * (budget.percentage ?: 0.0) / 100.0
                }
            }
            // A category's spending comes back per currency, and the currencies of two
            // categories of the same budget need not agree — so the categories are added
            // by currency and only then reduced. The currency of the *limit* is what the
            // spending must be reduced to; until the limit has one of its own (task 8.5),
            // that is the single currency the app has.
            val spent = budget.categories
                .filter { it.type.isExpense }
                .fold(CurrencyBalance.zero) { total, category ->
                    total + (categoryBalances[category.dimensionId] ?: CurrencyBalance.zero)
                }[ASSUMED_SINGLE_CURRENCY]
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
