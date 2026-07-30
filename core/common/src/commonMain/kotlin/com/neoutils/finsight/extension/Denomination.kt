package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable

/**
 * How a monetary figure is denominated: the currency it is expressed in, and whether
 * getting to it went through a currency conversion.
 *
 * The two travel together because they answer the same question — *what this number
 * means* — and because both are lost the same way. A figure that changes currency while
 * the symbol beside it does not renders right with the wrong legend; a figure that loses
 * its approximation mark on the way to the screen is indistinguishable from an exact one.
 * Carrying them in one type makes "currency without exactness" unexpressible, the way
 * [DisplayAmount] already makes "value without sign policy" unexpressible.
 *
 * [isApproximate] is **derived**, never declared by a screen: it is the consolidation
 * layer — the single owner of reducing a per-currency result to one figure — that knows
 * whether a conversion took part. Every other producer denominates with [exact], because
 * a figure the ledger returned in a single currency *is* exact, whatever that currency is.
 */
@Immutable
class Denomination private constructor(
    val currency: String,
    val isApproximate: Boolean,
    /**
     * The rates that produced this figure, empty whenever it is exact. They are carried
     * because the surface that shows the mark has to be able to explain it, and the only
     * place that knows which quote governed the number is the place that applied it.
     */
    val appliedRates: List<AppliedRate> = emptyList(),
) {

    override fun equals(other: Any?) = other is Denomination &&
            currency == other.currency &&
            isApproximate == other.isApproximate &&
            appliedRates == other.appliedRates

    override fun hashCode(): Int {
        var result = currency.hashCode()
        result = 31 * result + isApproximate.hashCode()
        result = 31 * result + appliedRates.hashCode()
        return result
    }

    override fun toString() = "Denomination($currency${if (isApproximate) ", approximate" else ""})"

    companion object {
        /** A figure no conversion took part in — the common case, and every ledger read of one currency. */
        fun exact(currency: String) = Denomination(currency, isApproximate = false)

        /**
         * A figure some conversion produced. Only the consolidation layer may denominate
         * this way: exactness is derived from the per-currency result and the available
         * rates, and marking it by hand is the failure this restriction exists to prevent.
         *
         * [appliedRates] is not optional: a figure that says it is approximate without
         * saying by what cannot be explained to the reader, and the surface that shows the
         * mark is required to explain it (design D25).
         */
        fun approximate(currency: String, appliedRates: List<AppliedRate>) =
            Denomination(currency, isApproximate = true, appliedRates = appliedRates)
    }
}
