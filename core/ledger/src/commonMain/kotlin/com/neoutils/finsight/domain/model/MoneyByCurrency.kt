package com.neoutils.finsight.domain.model

/**
 * One denominated figure: [value] in the major unit of [currency] (design D14 keeps
 * the read boundary in `Double`). It exists so a caller that reduces a
 * [MoneyByCurrency] gets the currency back along with the number, instead of
 * carrying it by hand from somewhere else.
 */
data class CurrencyAmount(val currency: String, val value: Double)

/**
 * The result of a ledger read that can span accounts of different currencies: one
 * value per currency, never a single number.
 *
 * The ledger never consolidates (design D8). A read that could sum reais with
 * dollars into one figure would stop being `Σ entries` and start being
 * `Σ (entries × rate)` — the first ledger read to depend on something that is not
 * the ledger. Returning per currency keeps the sentence literally true and pushes
 * the approximation up to where it is a presentation choice.
 *
 * **Adding two of these is the ledger's job, and this is the only implementation.**
 * A summary that needs the total of two disjoint perimeters (the dashboard's
 * neutral flow is `asset.expense + liability.expense`) gets it from [plus]. It is
 * not the consolidation layer's, which answers only for conversion between
 * currencies, and it cannot be the display type's, which must never combine two
 * values (`money-display`). Without one owner it would be written inline at each
 * call site, which is the reimplementation of a derivable rule the ledger forbids.
 *
 * **A zero is kept, and absence is not the same as zero.** `{USD: 0}` says "there
 * is movement in dollars and it sums to zero"; the empty figure says "there is no
 * movement at all". A user whose accounts are all in dollars and whose balance is
 * zero must read `US$ 0,00` — dropping the key would lose the only currency in the
 * figure and the consolidation layer would fall back to the base, printing `R$ 0,00`
 * over a dollar balance (design D9, D29). The SQL gives this for free: a grouped
 * aggregate returns the row `{USD: 0}` when entries exist and no rows when they
 * do not.
 */
class MoneyByCurrency private constructor(
    private val amounts: Map<String, Double>,
) {

    /** The currencies this figure is denominated in, in code order. */
    val currencies: Set<String> get() = amounts.keys

    val isEmpty: Boolean get() = amounts.isEmpty()

    val isNotEmpty: Boolean get() = amounts.isNotEmpty()

    /**
     * The value denominated in [currency], or `null` when the figure says nothing
     * about it. Nullable on purpose: `null` and `0.0` are different facts here.
     */
    operator fun get(currency: String): Double? = amounts[currency]

    /**
     * Each currency summed with its own, no conversion. The identity is [zero], so
     * a fold over many figures needs no special case for the first one.
     */
    operator fun plus(other: MoneyByCurrency): MoneyByCurrency {
        if (other.isEmpty) return this
        if (isEmpty) return other
        val sum = amounts.toMutableMap()
        other.amounts.forEach { (currency, value) ->
            sum[currency] = (sum[currency] ?: 0.0) + value
        }
        return of(sum)
    }

    /**
     * The single term of a figure that has exactly one, or `null` when it is empty
     * or spans more than one currency.
     *
     * A facade that knows its figure is mono-currency by its own guarantee — an
     * invoice's dimension always falls on one card — reduces it here, at the call
     * site where that guarantee is written. The ledger never presumes it: nothing
     * in the ledger ties a dimension to a single account (design D8).
     */
    fun singleOrNull(): CurrencyAmount? =
        amounts.entries.singleOrNull()?.let { CurrencyAmount(it.key, it.value) }

    /** The terms of the figure, in currency-code order — the one way to iterate it. */
    fun toList(): List<CurrencyAmount> = amounts.map { CurrencyAmount(it.key, it.value) }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MoneyByCurrency && amounts == other.amounts)

    override fun hashCode(): Int = amounts.hashCode()

    override fun toString(): String =
        amounts.entries.joinToString(prefix = "MoneyByCurrency(", postfix = ")") {
            "${it.key}=${it.value}"
        }

    companion object {

        /** The figure that says nothing: no currency, no value. */
        val zero = MoneyByCurrency(emptyMap())

        fun of(currency: String, value: Double) = MoneyByCurrency(mapOf(currency to value))

        /**
         * The figure a grouped aggregate produces. Keys are ordered by currency code
         * so that two reads of the same data list their terms in the same order —
         * SQL row order is not a promise, and a figure of two terms is rendered as
         * two lines (design D22).
         */
        fun of(amounts: Map<String, Double>) = MoneyByCurrency(
            amounts.entries
                .sortedBy { it.key }
                .associate { it.key to it.value },
        )
    }
}
