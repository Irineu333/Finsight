package com.neoutils.finsight.ui.screen.exchangeRates

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate

/**
 * The rate **in force** for one pair — the observation that answers for it today, under
 * the archive's own policy.
 *
 * One row per pair and not one per observation, and it is **elected by the archive's
 * query** rather than reduced here: which observation answers is the policy's decision, it
 * has exactly one owner, and re-deriving it in a view model would give a derived rule a
 * second one.
 *
 * [isOutdated] is computed in the ViewModel because it is a question about *when*, and a
 * composable that asks the clock recomputes on nothing. Thirty days is not derivable from
 * the domain; it is an opinion about volatility, stated once.
 */
data class ExchangeRateInForce(
    val rate: ExchangeRate,
    val isOutdated: Boolean,
)

/**
 * The state of the automatic upkeep, as this screen states it — **and this screen is the
 * only place in the app that states it**.
 *
 * The ban on loading states is about a **consolidated figure**: a balance may carry no
 * spinner and may not fail. This screen is not a figure — it is the archive explaining
 * itself, and it is where the *out of date for more than 30 days* signal already lives.
 * The two coexist rather than replacing one another: without knowing whether the app
 * managed to update, the user cannot tell a stale rate they never entered from one the app
 * could not fetch.
 *
 * @param lastSyncedOn when the archive was last brought up to date successfully, or `null`
 * when it never has been. `null` is presented as *not updated yet* and deliberately not as
 * some date: the two lead to different things and only one of them is true.
 * @param notCoveredCurrencies the currencies **in use** the source refused to quote. A
 * second state and not the same one, because the actions differ — wait, or enter the rate
 * by hand, which is permanent — and only the distinction is actionable.
 */
data class RateSyncStatus(
    val lastSyncedOn: LocalDate? = null,
    val notCoveredCurrencies: List<String> = emptyList(),
)

data class ExchangeRatesUiState(
    val baseCurrency: String,
    val inForce: List<ExchangeRateInForce> = emptyList(),
    val sync: RateSyncStatus = RateSyncStatus(),
    val isLoading: Boolean = true,
) {
    val isEmpty get() = !isLoading && inForce.isEmpty()
}
