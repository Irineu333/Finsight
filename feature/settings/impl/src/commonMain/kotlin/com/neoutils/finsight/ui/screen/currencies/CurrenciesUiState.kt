package com.neoutils.finsight.ui.screen.currencies

import com.neoutils.finsight.domain.model.CurrencyInfo

/**
 * One row of the registry: the currency, and whether it is still offered.
 *
 * [isBase] travels with the row so the screen it opens does not have to ask again which
 * currency is the base — and the base is the one row with no retire action at all.
 */
data class CurrencyItem(
    val currency: CurrencyInfo,
    val isArchived: Boolean,
    val isBase: Boolean,
) {
    /** The row's label: the name it has, and the code when it has none. */
    val label: String get() = currency.name ?: currency.code
}

data class CurrenciesUiState(
    val currencies: List<CurrencyItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty get() = !isLoading && currencies.isEmpty()
}
