@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ExchangeRatesViewModel(
    private val exchangeRateRepository: IExchangeRateRepository,
    baseCurrencyRepository: IBaseCurrencyRepository,
) : ViewModel() {

    val uiState = combine(
        exchangeRateRepository.observeAll(),
        baseCurrencyRepository.observe(),
    ) { rates, base ->
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        ExchangeRatesUiState(
            baseCurrency = base,
            rates = rates.map { ExchangeRateUi(rate = it, isOutdated = it.date.isOutdatedOn(today)) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExchangeRatesUiState(
            baseCurrency = baseCurrencyRepository.current(),
            rates = emptyList(),
        ),
    )

    fun onAction(action: ExchangeRatesAction) {
        when (action) {
            is ExchangeRatesAction.Remove -> viewModelScope.launch {
                exchangeRateRepository.remove(action.rate)
            }
        }
    }

    private fun LocalDate.isOutdatedOn(today: LocalDate) = daysUntil(today) > OUTDATED_AFTER_DAYS

    private companion object {
        /**
         * Not derivable from the domain — it is an opinion about volatility. Flagging rather
         * than merely showing the date exists because the consequence of a stale rate is a
         * past month's figure shown wrong, and that consequence is invisible from wherever
         * the user happens to be standing.
         */
        const val OUTDATED_AFTER_DAYS = 30
    }
}

data class ExchangeRatesUiState(
    val baseCurrency: String,
    val rates: List<ExchangeRateUi>,
) {
    val isEmpty = rates.isEmpty()
}

data class ExchangeRateUi(
    val rate: ExchangeRate,
    val isOutdated: Boolean,
)

sealed interface ExchangeRatesAction {
    data class Remove(val rate: ExchangeRate) : ExchangeRatesAction
}
