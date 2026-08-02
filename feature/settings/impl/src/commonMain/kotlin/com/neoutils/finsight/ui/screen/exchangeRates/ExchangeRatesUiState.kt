package com.neoutils.finsight.ui.screen.exchangeRates

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
    val isOutdated: Boolean,
)

/**
 * The observations priced **in** one currency — *what a euro, a dollar and a yen are
 * worth in reais*.
 *
 * A rate has two ends, so grouping has to pick one. The counterpart end is the one that
 * actually gathers: in the ordinary archive every rate is priced in the base, so keying
 * on the priced currency would put every row in a group of its own and group nothing.
 * Keying here collects them under the one heading that is true of all of them, and the
 * heading is the sentence the user came to read.
 *
 * **Consequence accepted:** after a base switch the same pair may appear under two
 * headings, one per direction. They are two distinct observations, and this screen shows
 * observations — never one of them inverted to join the other.
 */
data class ExchangeRateGroup(
    /**
     * The currency every rate of the group is priced **in**, as its ISO code.
     *
     * The code alone, and not the catalog's name beside it: every row underneath ends in
     * that same code, so spelling the currency out in the heading would say a third time
     * what the rows already say — and a heading is a label, not a sentence.
     */
    val counterCurrency: String,
    val rates: List<ExchangeRateItem>,
)

data class ExchangeRatesUiState(
    val baseCurrency: String,
    val groups: List<ExchangeRateGroup> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty get() = !isLoading && groups.isEmpty()
}
