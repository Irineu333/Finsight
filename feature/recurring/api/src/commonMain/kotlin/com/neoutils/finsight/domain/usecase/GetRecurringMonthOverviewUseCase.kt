package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringCycles
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository

/**
 * A month of recurrings in the two things it is made of — **fact** and **forecast**.
 *
 * The four money figures are raw, per currency: reducing them to something a screen
 * reads is conversion, and conversion lives above this, in the consolidation layer. What
 * is decided here is *which money belongs where*.
 *
 * [undenominated] is the templates left out of the forecast because no account or card
 * denominates them. It is carried rather than swallowed: a total that discards parcels
 * silently is indistinguishable from a complete one, and the user it fails is exactly
 * the one whose template points at an account that no longer exists. It is not a count
 * of cycles — no section of the list accounts for it, and the way out of it is pointing
 * the template somewhere real.
 */
data class RecurringMonthOverview(
    val settledExpense: MoneyByCurrency,
    val settledIncome: MoneyByCurrency,
    val forecastExpense: MoneyByCurrency,
    val forecastIncome: MoneyByCurrency,
    val undenominated: Int,
)

/**
 * The owner of "what does this month of recurrings amount to".
 *
 * The two halves are asked for, never recomputed: the forecast is drawn from the cycles
 * the month has nothing recorded for, straight off the partition the list is built from,
 * and the fact is the ledger read of the confirmed cycles. Both already have owners, and
 * this one only puts them side by side.
 *
 * **The asymmetry between the halves is deliberate.** The fact is an assertion about
 * *money*: it stays in the month it was posted in, including when the template was
 * archived right after — the posting happened, it is in the ledger, and it is still tied
 * to the template. The forecast is an assertion about *templates*: an archived one
 * generates nothing, in any month, so it composes neither forecast figure.
 *
 * @param cycles the month's partition, resolved once by the caller and shared with the
 * list it is already building from it. The month is [RecurringCycles.month] — there is no
 * second month to pass, and therefore no second month to disagree with.
 * @param currencyOf what denominates a template, resolved by the caller. It arrives as a
 * function rather than being read here because the rule lives on the account repository,
 * which an `api` module cannot name — and because the caller resolves the whole list
 * once per emission and shares the answer with the list it is already denominating.
 */
class GetRecurringMonthOverviewUseCase(
    private val occurrenceRepository: IRecurringOccurrenceRepository,
) {
    suspend operator fun invoke(
        cycles: RecurringCycles,
        currencyOf: (Recurring) -> String?,
    ): RecurringMonthOverview {
        val settled = occurrenceRepository.settledIn(cycles.month)

        var forecastExpense = MoneyByCurrency.zero
        var forecastIncome = MoneyByCurrency.zero
        var undenominated = 0

        cycles.unhandled.forEach { cycle ->
            val recurring = cycle.recurring
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

        return RecurringMonthOverview(
            settledExpense = settled.expense,
            settledIncome = settled.income,
            forecastExpense = forecastExpense,
            forecastIncome = forecastIncome,
            undenominated = undenominated,
        )
    }
}
