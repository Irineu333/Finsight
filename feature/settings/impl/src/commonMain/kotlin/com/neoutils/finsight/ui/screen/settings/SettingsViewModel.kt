package com.neoutils.finsight.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The preferences hub, which today holds exactly one figure of its own: the base currency.
 *
 * It is shown rather than edited. The v1 does not offer changing it — the base is resolved
 * from the device's locale once, and moving it would restate every consolidated figure in
 * the user's history. Showing it is still worth a line, because the mark on an approximate
 * figure is otherwise unexplained: this is where "reduced to what?" is answered.
 */
class SettingsViewModel(
    baseCurrencyRepository: IBaseCurrencyRepository,
) : ViewModel() {

    val uiState = baseCurrencyRepository.observe()
        .map(::SettingsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(baseCurrencyRepository.current()),
        )
}

data class SettingsUiState(
    val baseCurrency: String,
)
