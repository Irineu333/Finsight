package com.neoutils.finsight.ui.screen.exchangeRateHistory

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate

/**
 * An observation together with everything the row says about it.
 *
 * [isOutdated] is computed in the ViewModel rather than in the row because it is a
 * question about *when*, and a composable that asks the clock recomputes on nothing.
 * Thirty days is not derivable from the domain; it is an opinion about volatility, and it
 * is stated once.
 */
data class ExchangeRateHistoryItem(
    val rate: ExchangeRate,
    val isOutdated: Boolean,
)

/**
 * The observations priced **in** one currency — *what a euro, a dollar and a yen were
 * worth in reais*.
 *
 * A rate has two ends, so grouping has to pick one. The counterpart end is the one that
 * actually gathers: in the ordinary archive every rate is priced in the base — and the
 * automatic upkeep makes that more ordinary still, because it writes in exactly that
 * direction — so keying on the priced currency would put every row in a group of its own
 * and group nothing.
 *
 * **Consequence accepted:** the same pair may appear under two headings, one per
 * direction. They are two distinct observations, and this screen shows observations —
 * never one of them inverted to join the other.
 */
data class ExchangeRateHistoryGroup(
    val counterCurrency: String,
    val rates: List<ExchangeRateHistoryItem>,
)

/**
 * What the history is narrowed by.
 *
 * **The filters are not decoration.** The automatic upkeep writes a row per pair per day,
 * so the archive becomes dense in weeks. Without them, reaching the observation one wants
 * to correct or remove would depend on scrolling — and removal, which exists as the
 * corollary of a rate outliving the operation that revealed it, would stop being
 * reachable in practice.
 *
 * @param currency matches an observation naming it on **either** end.
 */
data class ExchangeRateHistoryFilters(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val currency: String? = null,
    val source: ExchangeRate.Source? = null,
) {
    val isActive get() = start != null || end != null || currency != null || source != null
}

data class ExchangeRateHistoryUiState(
    val groups: List<ExchangeRateHistoryGroup> = emptyList(),
    val filters: ExchangeRateHistoryFilters = ExchangeRateHistoryFilters(),
    /** Every currency the archive names, on either end — what the currency filter offers. */
    val currencies: List<String> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty get() = !isLoading && groups.isEmpty()
}
