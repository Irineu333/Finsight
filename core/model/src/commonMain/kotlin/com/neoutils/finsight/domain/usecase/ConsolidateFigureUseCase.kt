package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import com.neoutils.finsight.extension.MoneyFigure
import kotlinx.datetime.LocalDate

/**
 * The **one** reduction of a per-currency result to a figure a surface shows. Every consumer
 * of a consolidated figure goes through it, and no screen, ViewModel or UI model multiplies
 * by a rate on its own.
 *
 * The rule has two parts, and the first is the one that matters most:
 *
 * 1. **A single currency passes straight through, in its own currency, exact** — whatever
 *    that currency is, and whether or not a rate for it exists. There was nothing to
 *    reconcile, so converting would trade an exact number for an approximate one in exchange
 *    for nothing. This is what lets a user with every account in dollars and a Brazilian
 *    locale read the whole app exactly, unmarked, base currency nowhere in sight.
 * 2. **Two or more currencies reduce as far as the rates allow.** What converts joins one
 *    term in the base; what has no rate stays a term of its own. Nothing becomes `1`, nothing
 *    is dropped, and nothing empties the screen — the state "first foreign account created,
 *    rate not yet entered" is reachable by construction and has to read sensibly.
 *
 * Exactness is **derived** here and nowhere else: a figure is approximate when some
 * conversion took part in it, which is why [Denomination.approximate] is this layer's to
 * hand out and no surface can mark a figure by hand.
 *
 * Conversion lives here rather than in the ledger because a read that multiplied entries by
 * a rate would stop being `Σ entries`. Combining two per-currency results — summing disjoint
 * perimeters — is *not* this layer's business: that is arithmetic over balances, and the
 * ledger owns it ([CurrencyBalance.plus]).
 */
class ConsolidateFigureUseCase(
    private val exchangeRateRepository: IExchangeRateRepository,
) {

    /**
     * [balance] as one figure, denominated in [base] only where a conversion was needed, with
     * the rates in force on [date] — the last on or before it, so a past figure stays still.
     * [policy] is how each term reads, and it is the caller's: the consolidation of a balance
     * and of a spending line differ in sign, not in arithmetic.
     */
    suspend operator fun invoke(
        balance: CurrencyBalance,
        base: String,
        date: LocalDate,
        policy: SignPolicy = SignPolicy.NATURAL,
    ): MoneyFigure {
        val amounts = balance.entries

        // Nothing at all reads as zero, and it is exact: an empty result is not an unknown
        // one. It is denominated in the base because no account named a currency for it.
        if (amounts.isEmpty()) return MoneyFigure.of(term(0.0, base, policy, converted = false))

        amounts.entries.singleOrNull()?.let { (currency, amount) ->
            return MoneyFigure.of(term(amount, currency, policy, converted = false))
        }

        val (convertible, own) = amounts.entries
            .map { (currency, amount) -> Term(currency, amount, rateOf(currency, base, date)) }
            .partition { it.rate != null }

        // Everything the rates reached, in one term in the base. It is approximate because
        // some conversion happened — and it is approximate even when the base's own share is
        // the whole of it, since the figure as a whole passed through a rate.
        val convertedTotal = convertible.sumOf { it.amount * it.rate!! }
        val baseTerm = term(convertedTotal, base, policy, converted = true)

        // What no rate reached keeps its own currency, and keeps it exactly: that share of
        // the figure was not converted, and saying otherwise would be the invented value this
        // rule exists to refuse.
        val ownTerms = own
            .sortedBy { it.currency }
            .map { term(it.amount, it.currency, policy, converted = false) }

        return MoneyFigure.of(
            if (convertible.isEmpty()) ownTerms else listOf(baseTerm) + ownTerms
        )
    }

    /** The base is worth one of itself by definition, and no row may say otherwise. */
    private suspend fun rateOf(currency: String, base: String, date: LocalDate): Double? =
        if (currency == base) 1.0 else exchangeRateRepository.rateOn(currency, date)?.rate

    private fun term(value: Double, currency: String, policy: SignPolicy, converted: Boolean): DisplayAmount {
        val denomination = if (converted) Denomination.approximate(currency) else Denomination.exact(currency)
        return when (policy) {
            SignPolicy.MAGNITUDE -> DisplayAmount.magnitude(value, denomination)
            SignPolicy.NATURAL -> DisplayAmount.natural(value, denomination)
            SignPolicy.NEUTRAL -> DisplayAmount.neutral(value, denomination)
            SignPolicy.EXPLICIT_SIGN -> DisplayAmount.explicitSign(value, denomination)
            SignPolicy.FORCED_POSITIVE -> DisplayAmount.forcedPositive(value, denomination)
            SignPolicy.FORCED_NEGATIVE -> DisplayAmount.forcedNegative(value, denomination)
            SignPolicy.OWED -> DisplayAmount.owed(value, denomination)
        }
    }

    private class Term(val currency: String, val amount: Double, val rate: Double?)
}
