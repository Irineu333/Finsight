package com.neoutils.finsight.ui.screen.settings

import com.neoutils.finsight.domain.model.CurrencyInfo

/**
 * There is no `Loading` and no `Empty`: the base currency is a preference seeded on
 * first run, so it is always resolved by the time a screen can ask for it.
 */
data class SettingsUiState(
    val baseCurrency: CurrencyInfo?,
    val baseCurrencyCode: String,
)
