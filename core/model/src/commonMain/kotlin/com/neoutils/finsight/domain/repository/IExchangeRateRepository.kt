package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The rates the app knows, and the one policy for choosing among them.
 *
 * The locally recorded rate is the **only** authority in any conversion: no reading of this
 * app waits on a network, shows a loading state or fails because a service is down. An
 * external source may offer a suggested value inside the screen that edits a rate, and
 * nowhere else.
 */
interface IExchangeRateRepository {

    /**
     * The rate that governs a figure dated [date]: the **last one on or before** it, with a
     * rate the user typed winning over one collected from an operation on the same day.
     * `null` when nothing is known for [currency] by that date — which is a defined state,
     * not an error: the figure gains a term of its own instead of a guessed value.
     *
     * "On or before" is what keeps a past figure still. A rate recorded today never reaches
     * back and moves a month that is already closed.
     */
    suspend fun rateOn(currency: String, date: LocalDate): ExchangeRate?

    /** Every rate the user can see, newest first — the rates screen reacts to this. */
    fun observeAll(): Flow<List<ExchangeRate>>

    suspend fun getAll(): List<ExchangeRate>

    /**
     * Records a rate, replacing whatever was known for the same currency, date and origin.
     * Re-editing a cross-currency operation must not leave two collected rates for one day.
     */
    suspend fun record(rate: ExchangeRate)
}
