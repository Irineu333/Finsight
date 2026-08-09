package com.neoutils.finsight.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
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
    // The switch offers the registry **whole**, minus the archived ones: it is a
    // preference over what the app offers, and it is never conditioned on a rate
    // reaching the currency chosen.
    currencyRepository: ICurrencyRepository,
) : ViewModel() {

    // Observed rather than read once, and it is the mechanism in use now rather than
    // preparation for a future: switching emits here, and every figure on screen
    // re-expresses on the next read.
    private val baseCurrency = baseCurrencyRepository.observe()

    val uiState = combine(baseCurrency, currencyRepository.observeOffered(), ::stateOf)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = stateOf(baseCurrency.value, offered = emptyList()),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SwitchBaseCurrency -> viewModelScope.launch {
                baseCurrencyRepository.set(action.code)
            }
        }
    }

    private fun stateOf(code: String, offered: List<CurrencyInfo>) = SettingsUiState(
        baseCurrency = offered.firstOrNull { it.code == code },
        baseCurrencyCode = code,
        selectableCurrencies = offered,
    )
}
