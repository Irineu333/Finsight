package com.neoutils.finsight.ui.screen.exchangeRateHistory

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

/**
 * The full history of the archive — every observation, filterable.
 *
 * **Internal to the `impl` on purpose.** No other feature navigates here: the door is the
 * in-force view, which is the archive's entry, and an `api` declares only what is
 * externally navigable. What reaches this screen from outside the feature is
 * `ExchangeRatesRoute`, and that has not changed.
 *
 * @param currency when set, the history arrives pre-filtered by the currency of the row
 * that was tapped — which is how the in-force view reaches the history *of that pair*.
 * The filter names a currency on **either** end, so tapping `1 USD = 5,50 BRL` shows both
 * directions of the dollar, which are two distinct observations and read as such.
 */
@Serializable
data class ExchangeRateHistoryRoute(val currency: String? = null) : NavRoute
