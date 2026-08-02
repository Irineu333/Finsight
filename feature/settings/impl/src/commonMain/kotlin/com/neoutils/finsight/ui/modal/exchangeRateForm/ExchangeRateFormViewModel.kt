@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
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
 * [ExchangeRate.Source.USER] on the row, and the unique
 * `(currency, counterCurrency, date, source)` is what lets a correction coexist with the
 * observation it outranks rather than destroy it.
 *
 * **The pair is written as chosen.** The two ends are never ordered and the quotient is
 * never inverted to fit a canonical form (design D2): inverting to store would keep a
 * number nobody observed. Editing an existing observation therefore opens in the
 * direction it was made in.
 *
 * **A pair that leads nowhere can be registered, and that is accepted.** EUR/JPY under a
 * base of BRL with no bridge is inert, not wrong. Barring it would require this form to
 * know how to resolve paths — knowledge that belongs to the archive — in order to
 * prevent a harmless row.
 */
class ExchangeRateFormViewModel(
    private val existing: ExchangeRate?,
    baseCurrencyRepository: IBaseCurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
    private val currencyRepository: ICurrencyRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val base = baseCurrencyRepository.observe().value

    private val _uiState = MutableStateFlow(
        ExchangeRateFormUiState(
            from = existing?.currency.orEmpty(),
            to = existing?.counterCurrency ?: base,
            date = existing?.date ?: today(),
            rate = existing?.rate,
            isEditing = existing != null,
            selectableCurrencies = emptyList(),
        )
    )

    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            currencyRepository.observeOffered().collect { offered ->
                // A rate being **corrected** may name an archived currency, and the
                // correction has to stay possible: the row it already names is added
                // back to what this form presents. A *new* rate is never offered one
                // (design D7).
                val edited = listOfNotNull(existing?.currency, existing?.counterCurrency)
                    .filter { code -> offered.none { it.code == code } }
                    .mapNotNull { currencyRepository.get(it) }

                _uiState.update { state ->
                    state.copy(
                        selectableCurrencies = offered + edited,
                        from = state.from.ifBlank {
                            offered.firstOrNull { it.code != base }?.code.orEmpty()
                        },
                    )
                }
            }
        }
    }

    fun onAction(action: ExchangeRateFormAction) {
        when (action) {
            is ExchangeRateFormAction.SelectFrom ->
                _uiState.update { it.copy(from = action.currency) }

            is ExchangeRateFormAction.SelectTo ->
                _uiState.update { it.copy(to = action.currency) }

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
                    // As chosen, in the direction chosen. Never ordered, never inverted.
                    currency = state.from,
                    counterCurrency = state.to,
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
