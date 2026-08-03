@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The archive's **entry view**: the rate in force for each pair, and the state of the
 * upkeep that keeps them there.
 *
 * It answers the question the user actually brings to this screen — *which rate is being
 * used* — and it is the entry precisely because the automatic upkeep makes the archive
 * grow every day, which turns the full listing into something unreadable as a primary
 * presentation. The full listing did not disappear: it moved to the history, with filters.
 *
 * **This view model reduces nothing.** The row that answers for a pair comes from
 * [ExchangeRateRepository.observeInForce], elected by the archive's policy in SQL. What is
 * added here is the staleness opinion and the two upkeep states — and none of it is a
 * figure, which is why this is the one surface of the app where any of it appears.
 */
class ExchangeRatesViewModel(
    baseCurrencyRepository: IBaseCurrencyRepository,
    exchangeRateRepository: ExchangeRateRepository,
    rateSyncStateRepository: IRateSyncStateRepository,
    getAccountCurrencies: GetAccountCurrenciesUseCase,
) : ViewModel() {

    private val baseCurrency = baseCurrencyRepository.observe()

    private val currenciesInUse = flow { emit(getAccountCurrencies().inUse) }

    val uiState = combine(
        baseCurrency,
        exchangeRateRepository.observeInForce(today()),
        rateSyncStateRepository.observe(),
        currenciesInUse,
    ) { base, rates, syncState, inUse ->
        val staleBefore = today().minus(OUTDATED_AFTER_DAYS, DateTimeUnit.DAY)

        ExchangeRatesUiState(
            baseCurrency = base,
            // Grouped by the currency each row is priced **in**, the group with the most
            // recent observation first — the same key and the same order the history uses,
            // because it is the same question about the same rows.
            groups = rates
                .map { ExchangeRateInForce(rate = it, isOutdated = it.date < staleBefore) }
                .groupBy { it.rate.counterCurrency }
                .map { (counterCurrency, items) ->
                    ExchangeRateInForceGroup(
                        counterCurrency = counterCurrency,
                        rates = items.sortedByDescending { it.rate.date },
                    )
                }
                .sortedByDescending { group -> group.rates.first().rate.date },
            sync = RateSyncStatus(
                lastSyncedOn = syncState.lastSyncedAt
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
                // Only the currencies the user actually holds: naming one they do not use
                // would be noise, and the sentence this state exists to make — *enter
                // this one by hand* — would have no addressee.
                notCoveredCurrencies = inUse.filter { it in syncState.notCoveredCurrencies }.sorted(),
            ),
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
         * than merely showing the date exists because the consequence of a stale rate (a
         * past period's figure displayed wrong) is not visible from where the user is
         * standing.
         */
        const val OUTDATED_AFTER_DAYS = 30
    }
}
