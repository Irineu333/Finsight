@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Entering a rate by hand, which is the other half of a rate being collected from an
 * operation: a currency the user holds but has never exchanged has no rate at all until
 * someone says what it is worth.
 *
 * Whatever is saved here is recorded as [ExchangeRate.Source.USER], and that is not a label
 * — it is what makes it win over a collected rate on the same day. The one a person typed
 * is the more deliberate statement about that date.
 */
class ExchangeRateFormViewModel(
    private val rate: ExchangeRate?,
    private val base: String,
    private val exchangeRateRepository: IExchangeRateRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExchangeRateFormUiState(
            isEditMode = rate != null,
            base = base,
            // Every currency the app offers except the base, which is worth one of itself by
            // definition and must never become a row that could be edited into saying
            // otherwise.
            currencies = CurrencyCatalog.offered.filterNot { it == base },
            currency = rate?.currency ?: CurrencyCatalog.offered.first { it != base },
            rate = rate?.rate?.toString().orEmpty(),
            date = rate?.date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()),
        )
    )

    val uiState = _uiState.asStateFlow()

    init {
        loadSuggestion()
    }

    fun onAction(action: ExchangeRateFormAction) {
        when (action) {
            is ExchangeRateFormAction.CurrencyChanged -> {
                _uiState.update { it.copy(currency = action.currency) }
                loadSuggestion()
            }

            is ExchangeRateFormAction.RateChanged ->
                _uiState.update { it.copy(rate = action.rate.filter { char -> char.isDigit() || char == '.' }) }

            is ExchangeRateFormAction.DateChanged -> {
                _uiState.update { it.copy(date = action.date) }
                loadSuggestion()
            }

            ExchangeRateFormAction.Save -> save()
        }
    }

    /**
     * What the app already knows for this currency on this date, offered as a placeholder
     * and never as a value. Suggesting is not deciding: a rate only exists because someone
     * stated it, and pre-filling the field would restate an old quote as a new one.
     */
    private fun loadSuggestion() {
        val state = _uiState.value

        viewModelScope.launch {
            val known = exchangeRateRepository.rateOn(state.currency, state.date)
            _uiState.update { it.copy(suggestion = known?.takeIf { known -> known != rate }) }
        }
    }

    private fun save() {
        val state = _uiState.value
        val value = state.rate.toDoubleOrNull() ?: return

        viewModelScope.launch {
            // The row this replaces is keyed by currency, date and source, so editing a rate
            // the user already typed corrects it in place rather than leaving two.
            exchangeRateRepository.record(
                ExchangeRate(
                    currency = state.currency,
                    date = state.date,
                    rate = value,
                    source = ExchangeRate.Source.USER,
                )
            )

            // A rate whose currency or date was edited is a different row: the one it came
            // from would otherwise survive beside it, saying something else about that day.
            rate?.takeIf { it.currency != state.currency || it.date != state.date }
                ?.let { exchangeRateRepository.remove(it) }

            modalManager.dismissAll()
        }
    }
}

data class ExchangeRateFormUiState(
    val isEditMode: Boolean,
    val base: String,
    val currencies: List<String>,
    val currency: String,
    val rate: String,
    val date: LocalDate,
    val suggestion: ExchangeRate? = null,
) {
    val canSave = (rate.toDoubleOrNull() ?: 0.0) > 0.0
}

sealed interface ExchangeRateFormAction {
    data class CurrencyChanged(val currency: String) : ExchangeRateFormAction
    data class RateChanged(val rate: String) : ExchangeRateFormAction
    data class DateChanged(val date: LocalDate) : ExchangeRateFormAction
    data object Save : ExchangeRateFormAction
}
