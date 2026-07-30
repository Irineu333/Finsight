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
     * The single currency this figure is denominated in, or `null` when it is empty. It is
     * how a facade that guarantees its own dimension is single-currency — a card invoice
     * always lands on one card — reads the denomination back out.
     */
    val soleCurrency: String? get() = amounts.keys.singleOrNull()

    /**
     * The amount of that single currency, zero when there is nothing at all. It refuses
     * several currencies rather than picking one: the guarantee belongs to the caller, and a
     * figure that quietly returned one of two would be the silent wrong-currency reading
     * this whole change exists to make impossible.
     */
    val soleAmount: Double
        get() {
            if (isEmpty) return 0.0
            val currency = requireNotNull(soleCurrency) {
                "Expected a single-currency figure, got ${amounts.keys}"
            }
            return amounts.getValue(currency)
        }

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

/**
 * The total of many per-currency figures — the fold over [CurrencyBalance.plus], so summing
 * a list of them is still the one implementation the ledger owns rather than a loop each
 * caller writes.
 */
fun Iterable<CurrencyBalance>.sum(): CurrencyBalance =
    fold(CurrencyBalance.zero) { total, balance -> total + balance }
