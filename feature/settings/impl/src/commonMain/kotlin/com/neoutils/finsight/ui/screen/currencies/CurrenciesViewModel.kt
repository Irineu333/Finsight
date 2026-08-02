package com.neoutils.finsight.ui.screen.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The registry: every row, the archived ones marked rather than hidden.
 *
 * They are listed rather than hidden because unarchiving has to be reachable, and
 * because what an archived currency means — *no longer offered*, still perfectly valid
 * where it is already used — is only legible if the row is there to say it.
 */
class CurrenciesViewModel(
    private val currencyRepository: ICurrencyRepository,
    private val archiveCurrency: ArchiveCurrencyUseCase,
    private val deleteCurrency: DeleteCurrencyUseCase,
    baseCurrencyRepository: IBaseCurrencyRepository,
) : ViewModel() {

    private val error = MutableStateFlow<CurrenciesUiState>(CurrenciesUiState())

    val uiState = combine(
        currencyRepository.observeAll(),
        currencyRepository.observeOffered(),
        baseCurrencyRepository.observe(),
        error,
    ) { all, offered, base, state ->
        val offeredCodes = offered.map { it.code }.toSet()

        state.copy(
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

    /**
     * How many rate observations a deletion would take with it — asked *before* the
     * deletion, so the confirmation can state the number instead of hiding it.
     */
    suspend fun ratesRemovedBy(code: String): Int = deleteCurrency.ratesToRemove(code)

    fun onAction(action: CurrenciesAction) {
        when (action) {
            is CurrenciesAction.Archive -> viewModelScope.launch {
                archiveCurrency.archive(action.code)
                    .onLeft { failure -> error.value = error.value.copy(error = failure.toUiText()) }
            }

            is CurrenciesAction.Unarchive -> viewModelScope.launch {
                archiveCurrency.unarchive(action.code)
            }

            is CurrenciesAction.Delete -> viewModelScope.launch {
                deleteCurrency(action.code)
                    .onLeft { failure -> error.value = error.value.copy(error = failure.toUiText()) }
            }

            CurrenciesAction.DismissError -> {
                error.value = error.value.copy(error = null)
            }
        }
    }
}
