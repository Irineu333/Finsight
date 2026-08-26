package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import kotlinx.datetime.YearMonth

/**
 * A month of recurrings in the two things it is made of — **fact** and **forecast** —
 * plus the counts that account for what neither can represent.
 *
 * The four money figures are raw, per currency: reducing them to something a screen
 * reads is conversion, and conversion lives above this, in the consolidation layer. What
 * is decided here is *which money belongs where*.
 *
 * [handled] and [total] are about the month's **active templates**; [skipped] counts the
 * ones handled by being skipped. A skipped cycle is neither a posting — there is no
 * entry — nor a pending one — the domain already counts it as handled — so it is
 * invisible in all four figures, and the counter is the only place it is representable.
 *
 * [undenominated] is the templates left out of the forecast because no account or card
 * denominates them. It is carried rather than swallowed: a total that discards parcels
 * silently is indistinguishable from a complete one, and the user it fails is exactly
 * the one whose template points at an account that no longer exists.
 */
data class RecurringMonthOverview(
    val settledExpense: MoneyByCurrency,
    val settledIncome: MoneyByCurrency,
    val forecastExpense: MoneyByCurrency,
    val forecastIncome: MoneyByCurrency,
    val handled: Int,
    val total: Int,
    val skipped: Int,
    val undenominated: Int,
)

/**
 * The owner of "what does this month of recurrings amount to".
 *
 * The two halves are asked for, never recomputed: the forecast is
 * [GetUnhandledRecurringUseCase] — the whole month, without the cut by day that makes a
 * template *pending* — and the fact is the ledger read of the confirmed cycles. Both
 * already have owners, and this one only puts them side by side.
 *
 * **The asymmetry between the halves is deliberate.** The fact is an assertion about
 * *money*: it stays in the month it was posted in, including when the template was
 * archived right after — the posting happened, it is in the ledger, and it is still tied
 * to the template. The forecast is an assertion about *templates*: an archived one
 * generates nothing, in any month, so it composes neither forecast figure.
 *
 * @param currencyOf what denominates a template, resolved by the caller. It arrives as a
 * function rather than being read here because the rule lives on the account repository,
 * which an `api` module cannot name — and because the caller resolves the whole list
 * once per emission and shares the answer with the list it is already denominating.
 */
class GetRecurringMonthOverviewUseCase(
    private val getUnhandledRecurring: GetUnhandledRecurringUseCase,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
) {
    suspend operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        month: YearMonth,
        currencyOf: (Recurring) -> String?,
    ): RecurringMonthOverview {
        val settled = occurrenceRepository.settledIn(month)

        val unhandled = getUnhandledRecurring(
            recurringList = recurringList,
            occurrences = occurrences,
            month = month,
        )

        var forecastExpense = MoneyByCurrency.zero
        var forecastIncome = MoneyByCurrency.zero
        var undenominated = 0

        unhandled.forEach { recurring ->
            val currency = currencyOf(recurring)
            if (currency == null) {
                undenominated++
                return@forEach
            }
            val money = MoneyByCurrency.of(currency, recurring.amount)
            if (recurring.type.isIncome) {
                forecastIncome += money
            } else {
                forecastExpense += money
            }
        }

        // The counter is about the templates the month can still ask something of, which
        // is the same set the forecast is drawn from — so it asks the same member the
        // forecast's owner asks, and "handled" is the complement of what that owner
        // returned. Neither half gets a predicate of its own to disagree with.
        val ofTheMonth = recurringList.filter { it.generatesCycleIn(month) }
        val idsOfTheMonth = ofTheMonth.mapTo(mutableSetOf()) { it.id }

        return RecurringMonthOverview(
            settledExpense = settled.expense,
            settledIncome = settled.income,
            forecastExpense = forecastExpense,
            forecastIncome = forecastIncome,
            handled = ofTheMonth.size - unhandled.size,
            total = ofTheMonth.size,
            skipped = occurrences.count {
                it.yearMonth == month &&
                    it.status == RecurringOccurrence.Status.SKIPPED &&
                    it.recurringId in idsOfTheMonth
            },
            undenominated = undenominated,
        )
    }
}
