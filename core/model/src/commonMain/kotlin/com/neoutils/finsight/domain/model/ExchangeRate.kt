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
    /**
     * Where a rate came from — which is what breaks a tie **on the same date**.
     *
     * The precedence is `USER` ▸ `REMOTE` ▸ `DERIVED`, and the declaration order below is
     * not it: the ranking is stated here, in prose, and implemented once, in the archive's
     * query.
     *
     * **Why the quote outranks the harvest.** A [DERIVED] rate is the quotient of a real
     * operation, so it *contains what the operation charged* — spread, tax, card fee. It
     * answers *how much it cost me*. A [REMOTE] one is the day's quotation of the pair,
     * and answers *how much it was worth*. Consolidating is **valuing** a net worth, not
     * reconstructing a cost, so when both exist for the same pair and day the second
     * question is the one being asked. [DERIVED] loses no reason to exist: it is the only
     * origin that works offline, the only one that reaches pairs outside the source's
     * coverage, and still what spares the user typing a number they already gave.
     *
     * **It breaks ties inside a date, and never over one.** A more recent observation wins
     * whatever either origin is. In particular a [USER] rate does not pin its pair against
     * later observations: it corrected the day it was an assertion about, and a correction
     * that silently governed the whole future is the defect dating the archive exists to
     * prevent.
     */
    enum class Source {
        /**
         * Derived from a transaction that crossed currencies: its two legs *are* the
         * observation, so the user never types the same rate twice.
         */
        DERIVED,

        /**
         * Obtained from the remote source that keeps the archive up to date — the day's
         * quotation of the pair, written by a synchronisation nothing waits on.
         */
        REMOTE,

        /** Typed by the user, and it prevails over both on the same date. */
        USER,
    }
}
