package com.neoutils.finsight.ui.screen.exchangeRates

import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.model.ExchangeRate

/**
 * A rate together with everything the row says about it.
 *
 * [isOutdated] is computed here rather than in the row because it is a question about
 * *when* — and a composable that asks the clock recomputes on nothing. Thirty days is
 * not derivable from the domain; it is an opinion about volatility, and it is stated
 * once, in the ViewModel.
 */
data class ExchangeRateItem(
    val rate: ExchangeRate,
    val currency: CurrencyInfo?,
    val isOutdated: Boolean,
)

/**
 * The observations of one **priced** currency — the one the rows answer *how much of*.
 *
 * A rate has two ends, so grouping by currency has to pick one or duplicate. The priced
 * end is the one picked, and the consequence is accepted: after a base switch the same
 * pair appears under two headings, one per direction, because they are two distinct
 * observations and this screen shows observations.
 */
data class ExchangeRateGroup(
    val currency: String,
    val info: CurrencyInfo?,
    val rates: List<ExchangeRateItem>,
)

data class ExchangeRatesUiState(
    val baseCurrency: String,
    val groups: List<ExchangeRateGroup> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty get() = !isLoading && groups.isEmpty()
}
