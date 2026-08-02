package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate

/**
 * What one currency was worth **in another** on one day.
 *
 * A rate is an **observation about the world**, not a property of a transaction. The
 * transaction that crossed currencies was the *occasion* of learning it, never its
 * owner — which is why deleting that transaction leaves the rate standing (design D27).
 * Deleting a wrong posting from March must not silently move March's net worth: March
 * depends on March's rate, which goes on being true about March whatever revealed it.
 *
 * It names **both** of its ends, so it reads on its own and its meaning does not move
 * when the base currency does. The direction is the one it was observed in, and it is
 * never canonicalised: inverting to store would keep a number nobody measured.
 */
data class ExchangeRate(
    val id: Long = 0,
    /** ISO 4217 code of the currency being priced — the one it answers *how much*. */
    val currency: String,
    /** ISO 4217 code of the currency [currency] is priced **in**. */
    val counterCurrency: String,
    /** The day this rate is an observation about. */
    val date: LocalDate,
    /**
     * Units of [counterCurrency] per **one** unit of [currency] — `USD`/`BRL` at `5.50`
     * reads *one dollar is worth 5.50 reais*.
     *
     * The full quotient, never the rounded form a screen shows: the four decimal places
     * of the rates screen are a formatting decision with an owner of its own, and
     * storing the displayed text would make every reading a compounding loss.
     */
    val rate: Double,
    val source: Source,
) {
    /** Where a rate came from — which is what decides who wins on the same date. */
    enum class Source {
        /**
         * Derived from a transaction that crossed currencies: its two legs *are* the
         * observation, so the user never types the same rate twice.
         */
        DERIVED,

        /** Typed by the user, and it prevails over a [DERIVED] one of the same date. */
        USER,
    }
}
