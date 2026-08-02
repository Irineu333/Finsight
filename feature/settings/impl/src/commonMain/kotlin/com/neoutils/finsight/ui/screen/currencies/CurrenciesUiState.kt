package com.neoutils.finsight.ui.screen.currencies

import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.util.UiText

/**
 * One row of the registry: the currency, and whether it is still offered.
 *
 * [isBase] is here because the base cannot be archived, and a row that offers an action
 * it will always refuse is worse than one that does not offer it.
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
    /**
     * Why the last action was refused, if it was — a currency an account or a budget
     * denominates cannot be deleted, and the base cannot be archived.
     */
    val error: UiText? = null,
) {
    val isEmpty get() = !isLoading && currencies.isEmpty()
}
