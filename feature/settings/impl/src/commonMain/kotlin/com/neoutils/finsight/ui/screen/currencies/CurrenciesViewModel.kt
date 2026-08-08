package com.neoutils.finsight.ui.screen.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The registry as a list, and nothing else.
 *
 * It carries no action, because a row carries none: editing, archiving and deleting
 * belong to the screen a row opens, which is where the app puts them for an account, a
 * card and a category. Keeping the actions here as well would be a second owner for
 * rules that already have one.
 */
class CurrenciesViewModel(
    currencyRepository: ICurrencyRepository,
    baseCurrencyRepository: IBaseCurrencyRepository,
) : ViewModel() {

    val uiState = combine(
        currencyRepository.observeAll(),
        currencyRepository.observeOffered(),
        baseCurrencyRepository.observe(),
    ) { all, offered, base ->
        val offeredCodes = offered.map { it.code }.toSet()

        CurrenciesUiState(
            currencies = all.map { currency ->
                CurrencyItem(
                    currency = currency,
                    isArchived = currency.code !in offeredCodes,
                    isBase = currency.code == base,
                )
            },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CurrenciesUiState(),
    )
}
