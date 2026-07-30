package com.neoutils.finsight.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    baseCurrencyRepository: IBaseCurrencyRepository,
) : ViewModel() {

    // Observed rather than read once, even though v1 offers no way to change it: what
    // the flow buys is that offering the change later is a screen, not a rewrite.
    private val baseCurrency = baseCurrencyRepository.observe()

    val uiState = baseCurrency
        .map(::stateOf)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = stateOf(baseCurrency.value),
        )

    private fun stateOf(code: String) = SettingsUiState(
        baseCurrency = CurrencyCatalog.of(code),
        baseCurrencyCode = code,
    )
}
