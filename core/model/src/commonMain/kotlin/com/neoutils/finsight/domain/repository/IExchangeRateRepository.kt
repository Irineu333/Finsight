package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The archive of exchange rates, and the one place the reading policy lives:
 * **the last rate on or before the date, the user's winning over a derived one of the
 * same date.**
 *
 * The stored rate is the only authority in any conversion. An external source may fill
 * the field in as a suggestion, inside the screen that edits a rate and nowhere else:
 * no read of this app waits on the network, shows a loading state, or fails when one is
 * unreachable.
 */
interface IExchangeRateRepository {

    /**
     * The rate in force for [currency] as of [date]. `null` is a defined state, not an
     * error: the app knows nothing about that currency on that day, so a figure that
     * needs it simply keeps a term of its own (design D9).
     */
    suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate?

    /**
     * The same question for every currency at once, keyed by currency — what a
     * consolidated figure spanning several of them needs, in one read instead of one
     * per term.
     */
    suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate>

    /**
     * The general form: how much of [to] one unit of [from] was worth as of [date],
     * whatever the base currency happens to be.
     *
     * The archive stores observations on pairs, in the direction each was made, so more
     * than one path may reach the same answer. **Which one wins is the implementation's
     * responsibility, and it is declared**: the direct observation, then the inverse,
     * then a single triangulation over a pivot — never two chained. Inside each level
     * the archive's own policy holds, and when nothing resolves the answer is `null`,
     * which MUST NOT be read as `1`.
     *
     * [rateAsOf] and [ratesAsOf] are the particular case *"against the base in force"*,
     * which is why the reducer never learns that the base can change.
     */
    suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate?

    /** The whole archive, newest first — what the rates screen lists. */
    fun observeAll(): Flow<List<ExchangeRate>>

    /**
     * Registers a rate, or corrects one.
     *
     * They are the same write and not two: what tells them apart is
     * [ExchangeRate.source], a field of the rate itself, and the unique
     * `(currency, counterCurrency, date, source)` is what lets a correction coexist with the
     * observation it corrects instead of destroying it.
     */
    suspend fun save(rate: ExchangeRate)

    /**
     * Removes a rate — the obligatory corollary of a rate outliving its transaction
     * (design D27), not a convenience. A rate observed by mistake from an operation
     * since deleted has no other path that reaches it, and correcting is not enough: it
     * has to be able to stop existing rather than be replaced by a guess.
     */
    suspend fun remove(rate: ExchangeRate)
}
