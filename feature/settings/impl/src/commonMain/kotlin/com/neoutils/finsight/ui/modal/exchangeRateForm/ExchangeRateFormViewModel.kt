@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Registering a rate and correcting one are the **same write**, and this is where that
 * shows: an [existing] rate seeds the fields, its absence starts them empty, and both
 * paths end in `save`. What tells the two apart downstream is
 * [ExchangeRate.Source.USER] on the row, and the unique `(currency, date, source)` is
 * what lets a correction coexist with the observation it outranks rather than destroy
 * it.
 */
class ExchangeRateFormViewModel(
    private val existing: ExchangeRate?,
    baseCurrencyRepository: IBaseCurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val base = baseCurrencyRepository.observe().value

    private val _uiState = MutableStateFlow(
        ExchangeRateFormUiState(
            baseCurrency = base,
            currency = existing?.currency
                ?: CurrencyCatalog.currencies.first { it.code != base }.code,
            date = existing?.date ?: today(),
            rate = existing?.rate,
            isEditing = existing != null,
            selectableCurrencies = CurrencyCatalog.currencies.filter { it.code != base },
        )
    )

    val uiState = _uiState.asStateFlow()

    fun onAction(action: ExchangeRateFormAction) {
        when (action) {
            is ExchangeRateFormAction.SelectCurrency ->
                _uiState.update { it.copy(currency = action.currency) }

            is ExchangeRateFormAction.SelectDate ->
                _uiState.update { it.copy(date = action.date) }

            is ExchangeRateFormAction.ChangeRate ->
                _uiState.update { it.copy(rate = action.rate) }

            ExchangeRateFormAction.Submit -> submit()
            ExchangeRateFormAction.Remove -> remove()
        }
    }

    /**
     * The dismissal belongs **inside** the write, as it does in every other form of this
     * app: dismissing a [ModalBottomSheet] clears its `ViewModelStore`, which cancels
     * this very scope — so a button that both submits and dismisses cancels its own
     * write at the first suspension point.
     */
    private fun submit() {
        val state = _uiState.value
        val rate = state.rate ?: return

        viewModelScope.launch {
            exchangeRateRepository.save(
                ExchangeRate(
                    // A correction keeps the row it corrects only when it *is* that
                    // row — a user rate edited stays the same row. Correcting a
                    // derived one writes a new `USER` row instead, leaving the
                    // operation's own observation standing.
                    id = existing?.id?.takeIf { existing.source == ExchangeRate.Source.USER } ?: 0,
                    currency = state.currency,
                    date = state.date,
                    rate = rate,
                    // Anything typed here is the user's, by definition, and it
                    // prevails over a derived rate of the same date.
                    source = ExchangeRate.Source.USER,
                )
            )
            modalManager.dismissAll()
        }
    }

    private fun remove() {
        val rate = existing ?: return
        viewModelScope.launch {
            exchangeRateRepository.remove(rate)
            modalManager.dismissAll()
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
