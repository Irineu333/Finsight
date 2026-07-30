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

data class ExchangeRatesUiState(
    val baseCurrency: String,
    val rates: List<ExchangeRateItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty get() = !isLoading && rates.isEmpty()
}
