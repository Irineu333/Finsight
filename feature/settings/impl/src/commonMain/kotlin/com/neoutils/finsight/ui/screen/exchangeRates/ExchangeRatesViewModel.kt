@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ExchangeRatesViewModel(
    baseCurrencyRepository: IBaseCurrencyRepository,
    exchangeRateRepository: IExchangeRateRepository,
) : ViewModel() {

    private val baseCurrency = baseCurrencyRepository.observe()

    val uiState = combine(
        baseCurrency,
        exchangeRateRepository.observeAll(),
    ) { base, rates ->
        val staleBefore = today().minus(OUTDATED_AFTER_DAYS, DateTimeUnit.DAY)

        ExchangeRatesUiState(
            baseCurrency = base,
            rates = rates.map { rate ->
                ExchangeRateItem(
                    rate = rate,
                    currency = CurrencyCatalog.of(rate.currency),
                    isOutdated = rate.date < staleBefore,
                )
            },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExchangeRatesUiState(baseCurrency = baseCurrency.value),
    )

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    companion object {
        /**
         * Not derivable from the domain — an opinion about volatility. Flagging rather
         * than merely showing the date exists because the consequence of a stale rate
         * (a past period's figure displayed wrong) is not visible from where the user
         * is standing.
         */
        const val OUTDATED_AFTER_DAYS = 30
    }
}
