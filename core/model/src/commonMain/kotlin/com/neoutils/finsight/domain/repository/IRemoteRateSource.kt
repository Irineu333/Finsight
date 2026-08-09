package com.neoutils.finsight.domain.repository

import kotlinx.datetime.LocalDate

/**
 * A quotation asked of the outside world, in one of the **three** shapes an answer can
 * have.
 *
 * They are three and not two on purpose. *Unavailable* and *not covered* look alike from
 * the call site and lead the user to opposite actions — wait, or enter the rate by hand,
 * which is permanent. Collapsing them into a `null` would put the user back in the worst
 * case with nothing explaining why (design D7).
 */
sealed interface RemoteQuote {

    /**
     * The source observed the pair, and says so with **its own** date.
     *
     * @param date the day the source declares the quotation is about — a source that
     * does not publish every day answers a Sunday with Friday's date, and writing today's
     * would invent an observation about a day nobody observed anything on (design D5).
     * @param rate the full quotient, never a rounded form: how much of the counter
     * currency one unit of the asked currency was worth.
     */
    data class Observed(val date: LocalDate, val rate: Double) : RemoteQuote

    /**
     * The source **refused the code explicitly**: it does not quote that currency, and it
     * never will. Waiting changes nothing here — the only path left is the user entering
     * the rate, and the screen is required to say so.
     */
    data object NotCovered : RemoteQuote

    /**
     * The source could not be reached, or answered something unreadable. Transport
     * failure and nothing more: it says nothing about the pair, and the right response is
     * to write nothing and try again next time.
     */
    data object Unavailable : RemoteQuote
}

/**
 * The outside quotation of a pair — **a writer of the rate archive, and never a path of
 * reading it** (design D1).
 *
 * Nothing waits on this port. The synchronisation calls it, writes what it learns as
 * ordinary archive rows, and the `Flow` over the table carries the result to every screen
 * by the path that already exists. That is what keeps the guarantee literal: no read, no
 * screen and no figure of this app depends on the network, shows a loading state, or
 * fails when it is unreachable. The guarantee is held by the **direction of the flow**,
 * not by forbidding the source to exist.
 *
 * **It names no provider**, and it holds no HTTP client: the client lives in the settings
 * feature, so that "only one module may reach the network" is a fact about the module
 * graph rather than a matter of discipline (design D11).
 */
interface IRemoteRateSource {

    /**
     * Every code the source quotes at all, or `null` when it could not be reached.
     *
     * **Coverage is asked about directly because a refused quotation cannot say which end
     * it is about.** A quotation names two currencies, and a refusal of `(XYZ, BRL)` is
     * equally consistent with *XYZ is not quoted* and with *BRL is not quoted*. Blaming
     * the first end is right in the ordinary case and wrong in the one that matters: when
     * the **base** is the uncovered code, every pair is refused, and the screen would name
     * every currency the user holds as unquoted — a list of false sentences, when the true
     * one is a single sentence about the base (design D7).
     *
     * It does not throw, and `null` is not an empty set: unknown coverage falls back to
     * asking pair by pair, while an empty set would mean the source quotes nothing.
     */
    suspend fun coverage(): Set<String>?

    /**
     * How much of [against] one unit of [currency] was worth, as the source last
     * published it.
     *
     * **The direction asked is part of the question, not something to fix on write.** The
     * archive is *everything priced in the base*, so the row wanted is `(currency in use,
     * base)`. Asking the cheap way round — one call for the base against every currency —
     * would answer *one real is worth 0.18 dollars* and store the archive inverted;
     * correcting that on write would mean inverting the quotient, which the archive
     * forbids outright, because it stores a number nobody observed (design D4).
     *
     * It does not throw. Unavailability is a **return value**, because failing here has
     * to mean doing nothing.
     */
    suspend fun quote(currency: String, against: String): RemoteQuote
}
