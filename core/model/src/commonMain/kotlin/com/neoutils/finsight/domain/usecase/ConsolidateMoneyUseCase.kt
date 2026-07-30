package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * The **one** reducer: a per-currency balance plus a reference date become the figure a
 * screen shows.
 *
 * It is the only place in the app where a rate multiplies anything. The ledger never
 * consolidates (design D8), the display type never combines two values (design D10),
 * and no screen, view model or UI model may do this arithmetic in line — which is what
 * makes "where did this number come from" answerable at all.
 *
 * The rule has two halves (design D9):
 *
 * 1. **One currency in the result → the figure *is* it, exact, whatever it is.** The
 *    base does not take part, with or without a known rate. There was nothing to
 *    reconcile, so converting would be pure loss: an approximate number in place of an
 *    exact one, in exchange for nothing. This is the half that keeps a user whose
 *    accounts are all in dollars, on a Brazilian device, reading dollars everywhere —
 *    exact, unmarked, dashboard included.
 * 2. **Two or more → reduce to the base as far as the rates allow**, one term per
 *    currency with no rate plus one base term holding everything that converted. The
 *    figure is approximate because a conversion happened.
 *
 * A missing rate never becomes `1`, never drops out of the sum and never blanks the
 * screen: it produces one more term, which is honest about what the app knows. That is
 * why "first foreign account created, no rate registered yet" — a state the real flow
 * *requires* — has defined and useful behaviour instead of undefined.
 *
 * Rounding is declared **here and nowhere else**: converted money is rounded to cents
 * once, at the moment it is converted.
 */
class ConsolidateMoneyUseCase(
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
) {

    /**
     * @param money what the ledger answered, per currency.
     * @param on the date whose rates apply — a figure about March is consolidated at
     * March's rates, or the past would move on its own whenever a rate changed.
     * @param policy how each term reads its own sign — a named constructor of
     * `DisplayAmount`, passed as a reference. It has no default because a figure carried
     * without its sign policy is exactly the failure `DisplayAmount` exists to prevent,
     * and the caller is the one who knows whether this is a balance, a magnitude or a
     * debt. The currency and the exactness are **not** the caller's to choose: this use
     * case is what derives them, and it fills them in itself.
     */
    suspend operator fun invoke(
        money: MoneyByCurrency,
        on: LocalDate,
        policy: (value: Double, currency: String, isApproximate: Boolean) -> DisplayAmount,
    ): ConsolidatedAmount {
        val base = baseCurrencyRepository.observe().value
        val terms = money.toList()

        if (terms.isEmpty()) {
            // Nothing at all. Zero is exact, and the base is the only currency there is
            // to say it in — no figure was reduced to get here.
            return ConsolidatedAmount(
                terms = listOf(policy(0.0, base, false)),
                isApproximate = false,
                baseIndex = 0,
            )
        }

        if (terms.size == 1) {
            val only = terms.single()
            return ConsolidatedAmount(
                terms = listOf(policy(only.value, only.currency, false)),
                isApproximate = false,
                baseIndex = if (only.currency == base) 0 else null,
            )
        }

        val rates = exchangeRateRepository.ratesAsOf(on)
        val (convertible, untouched) = terms.partition {
            it.currency == base || it.currency in rates
        }

        val converted = convertible.sumOf { term ->
            val rate = if (term.currency == base) 1.0 else rates.getValue(term.currency).rate
            (term.value * rate * CENTS_PER_UNIT).roundToLong()
        } / CENTS_PER_UNIT

        val baseTerm = convertible
            .takeIf { it.isNotEmpty() }
            ?.let { policy(converted, base, true) }

        return ConsolidatedAmount(
            // The base term first: design D22 gives the first term the surface's own
            // typographic weight, and a surface too narrow for the rest degrades to it.
            terms = listOfNotNull(baseTerm) +
                untouched.map { policy(it.value, it.currency, true) },
            asOf = on,
            // More than one currency went in, so something was reconciled — whether or
            // not every term could be. Exactness is a property of the figure, and two
            // exact terms placed side by side do not make one.
            isApproximate = true,
            baseIndex = if (baseTerm != null) 0 else null,
        )
    }

    private companion object {
        const val CENTS_PER_UNIT = 100.0
    }
}
