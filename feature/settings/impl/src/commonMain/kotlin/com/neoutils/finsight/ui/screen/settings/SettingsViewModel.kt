package com.neoutils.finsight.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings, and the switch of the base currency.
 *
 * **There is no `SwitchBaseCurrencyUseCase`, and its absence is the design** (D5).
 * Switching writes a preference and nothing else: no stored row moves, no migration
 * runs, nothing is re-expressed on write. Giving that an owner in the domain would be
 * inventing domain where there is none — the whole re-expression is a read, and it has
 * an owner already.
 */
class SettingsViewModel(
    private val baseCurrencyRepository: IBaseCurrencyRepository,
) : ViewModel() {

    // Observed rather than read once, and it is the mechanism in use now rather than
    // preparation for a future: switching emits here, and every figure on screen
    // re-expresses on the next read.
    private val baseCurrency = baseCurrencyRepository.observe()

    val uiState = baseCurrency
        .map(::stateOf)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = stateOf(baseCurrency.value),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SwitchBaseCurrency -> viewModelScope.launch {
                baseCurrencyRepository.set(action.code)
            }
        }
    }

    private fun stateOf(code: String) = SettingsUiState(
        baseCurrency = CurrencyCatalog.of(code),
        baseCurrencyCode = code,
        selectableCurrencies = CurrencyCatalog.currencies,
    )
}
