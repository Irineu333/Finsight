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
 * Everything the archive observed on **one day**.
 *
 * **The axis is the date, and it is the only one that ages well.** The counterpart
 * currency is what the in-force view groups by, and rightly — a handful of rows, headed by
 * what they are priced in. Here it would do the opposite of grouping: the automatic upkeep
 * writes a row per pair per day, and in the ordinary archive everything is priced in the
 * base, so a counterpart heading collapses months of history into a single group of
 * hundreds of rows. That is precisely the *groups nothing* failure the counterpart was
 * chosen to avoid over there, reached from the other end. The date partitions the archive
 * along the very axis it grows on.
 *
 * The heading has to say nothing about currency because every row already does: each one
 * states its pair on both ends, its value and its origin. **Consequence accepted:** the
 * same pair observed in both directions on one day sits in one group, as two rows — which
 * is what they are — each in the direction it was observed in.
 */
data class ExchangeRateHistoryGroup(
    val date: LocalDate,
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
