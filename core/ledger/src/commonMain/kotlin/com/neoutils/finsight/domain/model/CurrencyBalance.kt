package com.neoutils.finsight.domain.model

/**
 * A money figure the ledger returns when the read could span accounts of different
 * currencies: one amount per currency, in the major unit, and **never** one number.
 *
 * The ledger does not consolidate. The sentence the module sustains is that every figure
 * is `Σ entries`, and a read that multiplied entries by a rate would stop being one — it
 * would be `Σ (entries × rate)`, the first read to depend on something that is not the
 * ledger. Returning per currency keeps the sentence literally true and pushes the
 * approximation up to where it is a presentation choice. "Base currency" is therefore not
 * an accounting fact but a display preference, and it does not appear here.
 *
 * For a user of one currency this is a map of one key, which is why nothing downstream
 * needs a compatibility branch: today's behaviour is the particular case of the general
 * one.
 */
class CurrencyBalance private constructor(private val amounts: Map<String, Double>) {

    /** The currencies this figure is made of — empty when there is nothing at all. */
    val currencies: Set<String> get() = amounts.keys

    val isEmpty: Boolean get() = amounts.isEmpty()

    /** Every amount with its currency, for the consolidation layer to reduce. */
    val entries: Map<String, Double> get() = amounts

    /** The amount denominated in [currency] — zero when this figure has none of it. */
    operator fun get(currency: String): Double = amounts[currency] ?: 0.0

    /**
     * The sum of two per-currency figures: each currency added to its own, no conversion
     * anywhere.
     *
     * It lives here because it is arithmetic over balances, and the ledger owns what a
     * figure is worth. The consolidation layer answers only for conversion between
     * currencies, and the display type may not combine two values at all — so without an
     * owner here, a consumer needing the total of two disjoint perimeters would add two
     * maps inline, which is the reimplementation of a derivable rule the ledger forbids.
     */
    operator fun plus(other: CurrencyBalance): CurrencyBalance {
        if (other.isEmpty) return this
        if (isEmpty) return other

        val summed = amounts.toMutableMap()
        other.amounts.forEach { (currency, amount) ->
            summed[currency] = (summed[currency] ?: 0.0) + amount
        }
        return CurrencyBalance(summed)
    }

    override fun equals(other: Any?) = other is CurrencyBalance && amounts == other.amounts

    override fun hashCode() = amounts.hashCode()

    override fun toString() = "CurrencyBalance($amounts)"

    companion object {
        /** Nothing at all — not "zero of the base currency", which would name one. */
        val zero = CurrencyBalance(emptyMap())

        fun of(currency: String, value: Double) = CurrencyBalance(mapOf(currency to value))

        fun of(amounts: Map<String, Double>) =
            if (amounts.isEmpty()) zero else CurrencyBalance(amounts.toMap())
    }
}
