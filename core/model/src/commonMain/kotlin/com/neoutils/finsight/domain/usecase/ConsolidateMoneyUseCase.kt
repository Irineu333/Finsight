package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyAmount
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
        val terms = money.significantTerms()

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

        // **Which term is approximate is not the same question as which figure is.** The
        // base term is approximate only if a rate actually reached it — money that was
        // already in the base and stayed there was converted by nothing. And a term no
        // rate touched is *exact*: it is the very amount the ledger answered, standing in
        // its own currency. Marking it would say the app is unsure of a number it knows
        // perfectly well.
        val convertedSomething = convertible.any { it.currency != base }

        val baseTerm = convertible
            .takeIf { it.isNotEmpty() }
            ?.let { policy(converted, base, convertedSomething) }

        return ConsolidatedAmount(
            // The base term first: design D22 gives the first term the surface's own
            // typographic weight, and a surface too narrow for the rest degrades to it.
            terms = listOfNotNull(baseTerm) +
                untouched.map { policy(it.value, it.currency, false) },
            asOf = on,
            // The **figure** is approximate all the same: it holds currencies that do not
            // add up, so it is not one number and no single number answers for it. That
            // is what the badge explains, and it is a different fact from which term a
            // rate passed through.
            isApproximate = true,
            baseIndex = if (baseTerm != null) 0 else null,
        )
    }

    /**
     * The common scale of a **family** of figures, for ordering them and for taking one
     * as a share of the whole — and never, ever for display.
     *
     * A ranking and a percentage need one number per figure on one scale, and a figure
     * of several terms has none. Two things follow, and both are why this lives here
     * rather than at each call site.
     *
     * **It is a property of the family, not of a figure.** `{USD: 50}` and `{BRL: 100}`
     * each have a perfectly definite magnitude and are mutually incomparable; only
     * something that sees all of them knows whether the common scale is a shared single
     * currency (and no rate is consulted at all) or the base by way of rates.
     *
     * **It uses the same rates, on the same date, as the figures the user is reading**,
     * so the ranking and the figures can never disagree — which they could if each
     * caller reached for a rate of its own. That is also why it is in this class: a rate
     * multiplies money in exactly one place, and this keeps that sentence literally true.
     *
     * The result carries **no currency**, deliberately. It is not a `DisplayAmount` and
     * cannot become one without somebody picking a currency by hand, which is precisely
     * the smell design D29 exists to catch. It is a sort key and a denominator.
     *
     * @return a magnitude per key, `null` for a figure nothing could be converted from —
     * a missing rate never becomes `1` and never becomes `0%`.
     */
    suspend fun <K : Any> comparativeMagnitudes(
        figures: Map<K, MoneyByCurrency>,
        on: LocalDate,
    ): ComparativeMagnitudes<K> {
        val currencies = figures.values.flatMapTo(mutableSetOf()) { money ->
            money.significantTerms().map { it.currency }
        }

        // One currency across the whole family — whichever it is — is the mono-currency
        // case of design D9: every magnitude is its own exact value, and no rate is read.
        // It is what keeps the ranking and the percentages of a single-currency user
        // byte-identical, by construction rather than by a test.
        if (currencies.size <= 1) {
            return ComparativeMagnitudes(
                magnitudes = figures.mapValues { (_, money) ->
                    money.significantTerms().singleOrNull()?.value ?: 0.0
                },
                isApproximate = false,
            )
        }

        val base = baseCurrencyRepository.observe().value
        val rates = exchangeRateRepository.ratesAsOf(on)

        return ComparativeMagnitudes(
            magnitudes = figures.mapValues { (_, money) ->
                val significant = money.significantTerms()
                val convertible = significant.filter { it.currency == base || it.currency in rates }
                if (convertible.isEmpty() && significant.isNotEmpty()) {
                    // Nothing about this figure can be placed on the scale. It is not
                    // zero — zero is an assertion — so it has no magnitude at all.
                    null
                } else {
                    convertible.sumOf { term ->
                        val rate = if (term.currency == base) 1.0 else rates.getValue(term.currency).rate
                        (term.value * rate * CENTS_PER_UNIT).roundToLong()
                    } / CENTS_PER_UNIT
                }
            },
            isApproximate = true,
        )
    }

    /**
     * The same money expressed in **one nominated currency** — the currency of a budget
     * limit (design D13), which is chosen once and never moves.
     *
     * Distinct from [invoke] on purpose: there the currency of the answer is *derived*,
     * and a single-currency figure is delivered in its own currency untouched. Here the
     * target is imposed by what the number is going to be compared against, so
     * converting into it is the whole point rather than a loss.
     *
     * Triangulation, not a second table: a rate is stored one way, currency → base, and
     * `value × rate(currency) ÷ rate(target)` re-expresses it against any other. That is
     * derivation (design D11), and it is why no matrix of pairs exists.
     */
    suspend fun reduceTo(
        target: String,
        money: MoneyByCurrency,
        on: LocalDate,
    ): ReducedAmount {
        val terms = money.significantTerms()
        if (terms.isEmpty()) return ReducedAmount(0.0, isApproximate = false, hasUnconvertedPart = false)

        terms.singleOrNull()?.takeIf { it.currency == target }?.let {
            // Already in the currency asked for, and alone in it: exact, no rate read.
            return ReducedAmount(it.value, isApproximate = false, hasUnconvertedPart = false)
        }

        val base = baseCurrencyRepository.observe().value
        val rates = exchangeRateRepository.ratesAsOf(on)
        fun rateOf(currency: String): Double? =
            if (currency == base) 1.0 else rates[currency]?.rate

        val targetRate = rateOf(target)
        var cents = 0L
        var hasUnconverted = false

        terms.forEach { term ->
            if (term.currency == target) {
                cents += (term.value * CENTS_PER_UNIT).roundToLong()
                return@forEach
            }
            val rate = rateOf(term.currency)
            if (rate == null || targetRate == null) {
                // No rate reaches this part, so it stays out of the number and says so.
                // Leaving it in at `1` would be inventing; dropping it silently would be
                // worse than either.
                hasUnconverted = true
                return@forEach
            }
            cents += (term.value * rate / targetRate * CENTS_PER_UNIT).roundToLong()
        }

        return ReducedAmount(
            value = cents / CENTS_PER_UNIT,
            isApproximate = true,
            hasUnconvertedPart = hasUnconverted,
        )
    }

    private companion object {
        const val CENTS_PER_UNIT = 100.0
    }
}

/**
 * The terms of a figure that actually say something, in the order the ledger answered.
 *
 * A currency whose amount is exactly zero is **not** a share of the figure — it is a
 * currency the user happens to hold an account in, sitting empty. Carried into the
 * reduction it would turn `{BRL: 1000, USD: 0}` into a two-term approximate figure: the
 * `≈` mark over a number nothing was converted for, and, with no dollar rate on file,
 * `R$ 1.000,00 + US$ 0,00` on the dashboard forever. Opening a second account and
 * spending it back to zero is not an event that should mark every total in the app.
 *
 * The zeros are dropped **only where another currency survives**. A figure that is
 * nothing but zero keeps its own denomination — a dollar account with no movement reads
 * `US$ 0,00` and not the base's zero, which is the same rule as everywhere else: the
 * currency of a figure is the figure's own.
 */
private fun MoneyByCurrency.significantTerms(): List<CurrencyAmount> {
    val terms = toList()
    if (terms.size < 2) return terms
    return terms.filter { it.value != 0.0 }.ifEmpty { terms }
}

/**
 * Money re-expressed in one nominated currency, and how much of it actually got there.
 *
 * [hasUnconvertedPart] is what keeps the number from lying by omission: a progress bar
 * fed by a figure with an unpriced part is a **floor**, not a measurement, and the
 * surface has to be able to say so.
 */
data class ReducedAmount(
    val value: Double,
    val isApproximate: Boolean,
    val hasUnconvertedPart: Boolean,
)

/**
 * One number per figure, on a scale they share — for ranking and for proportion.
 *
 * Built only by [ConsolidateMoneyUseCase.comparativeMagnitudes]; the constructor is
 * internal so nothing else can fabricate a magnitude out of a currency it picked.
 */
class ComparativeMagnitudes<K> internal constructor(
    private val magnitudes: Map<K, Double?>,
    /** Whether a rate took part in building the scale. */
    val isApproximate: Boolean,
) {
    /** The scale's whole, over the figures that could be placed on it. */
    val total: Double = magnitudes.values.filterNotNull().sum()

    /** The sort key of one figure — `null` when nothing about it could be measured. */
    fun magnitudeOf(key: K): Double? = magnitudes[key]

    /**
     * One figure as a fraction of the whole, or `null` when there is no answer: nothing
     * of it converted, or the whole is zero. A surface shows a dash rather than `0%` —
     * zero is an assertion, and the absence of a rate is the absence of an answer.
     */
    fun shareOf(key: K): Double? =
        magnitudeOf(key)?.takeIf { total != 0.0 }?.let { it / total }
}
