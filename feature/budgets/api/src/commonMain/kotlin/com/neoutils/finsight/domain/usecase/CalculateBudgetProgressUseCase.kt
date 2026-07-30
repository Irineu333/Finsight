package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.dimensionBalancesInMonthByCurrency
import com.neoutils.finsight.extension.safeOnDay
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
 *
 * **The spending is reduced to the currency of the limit, never to the base.** A limit
 * is denominated once, when the budget is created, and the denomination comes from the
 * accounts the user actually transacts in (design D13) — the base answers *in what
 * currency totals are read*, which has nothing to say about a number the user typed. So
 * the comparison happens in the limit's currency, and it is **exact** whenever no
 * conversion took part: the single-currency user pays none of the cost of the
 * multi-currency one.
 *
 * When part of the spending sits in a currency no rate reaches, the progress is a
 * **floor** rather than a measurement, and it says so — a bar that quietly omitted that
 * part would read as "less spent than you have".
 */
class CalculateBudgetProgressUseCase(
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
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
        val categoryBalances = entryRepository.dimensionBalancesInMonthByCurrency(
            month = month,
            dimensionIds = budgets.flatMap { budget -> budget.categories.map { it.dimensionId } },
        )

        // A figure about March is reduced at March's rates, or a past month's progress
        // would move on its own whenever a rate changed.
        val on = month.safeOnDay(month.numberOfDays)
        return budgets.map { budget ->
            val limit = when (budget.limitType) {
                LimitType.FIXED -> budget.amount
                // **Non-Goal, and it is a real gap rather than an oversight.** A
                // `PERCENTAGE` limit is a share of a recurring's amount, and that
                // amount is denominated by the account the template names (design
                // D17) — which may not be the budget's currency. The number below
                // takes it as if it were, and nothing converts it. Closing that means
                // deciding whose date's rate applies to a limit that is re-derived
                // every month, which is a change of its own; until then the case is
                // out of scope and recorded here rather than silently wrong somewhere
                // downstream.
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
            // Each currency summed with its own, by the ledger's one implementation,
            // and only then reduced — to the limit's currency, which is what the number
            // is about to be compared against.
            val spentByCurrency = budget.categories
                .filter { it.type.isExpense }
                .fold(MoneyByCurrency.zero) { total, category ->
                    total + (categoryBalances[category.dimensionId] ?: MoneyByCurrency.zero)
                }
            val spent = consolidateMoney.reduceTo(
                target = budget.currency,
                money = spentByCurrency,
                on = on,
            )
            val recurring = if (budget.limitType == LimitType.PERCENTAGE) {
                recurringList.find { it.id == budget.recurringId }
            } else null
            BudgetProgress(
                budget = budget.copy(amount = limit),
                spent = spent.value,
                isApproximate = spent.isApproximate,
                hasUnpricedSpending = spent.hasUnconvertedPart,
                recurringLabel = recurring?.label,
                recurring = recurring,
            )
        }
    }
}
