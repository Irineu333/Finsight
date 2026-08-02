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

/**
 * The archive, grouped by the currency each observation **prices**.
 *
 * Grouping and its order are the only things this adds, and the order is the natural
 * extension of the `ORDER BY date DESC` the DAO already does: the currency with the most
 * recent observation first. What each row *means* does not depend on the heading above
 * it — the row states its own pair (design D9) — so a pair observed in both directions
 * legitimately appears under two headings.
 */
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
            groups = rates
                .map { rate ->
                    ExchangeRateItem(
                        rate = rate,
                        currency = CurrencyCatalog.of(rate.currency),
                        isOutdated = rate.date < staleBefore,
                    )
                }
                .groupBy { it.rate.currency }
                .map { (currency, items) ->
                    ExchangeRateGroup(
                        currency = currency,
                        info = CurrencyCatalog.of(currency),
                        rates = items.sortedByDescending { it.rate.date },
                    )
                }
                .sortedByDescending { group -> group.rates.first().rate.date },
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
