@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.exchangeRateHistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Every observation the archive holds, grouped by the currency each is priced **in**, and
 * narrowed by date, currency and origin.
 *
 * Grouping and its order are the only things this adds to the list, and the order is the
 * natural extension of the `ORDER BY date DESC` the DAO already does: the currency with
 * the most recent observation first. What each row *means* does not depend on the heading
 * above it — the row states its own pair — so a pair observed in both directions
 * legitimately appears under two headings.
 *
 * The filters are applied here and not in a query for one reason: they are a question
 * about presentation, not about which observation answers for a pair. That second
 * question is the archive's policy, it has an owner in the DAO, and this screen never
 * asks it — the history shows every row, elected or not.
 *
 * @param initialCurrency the pair this screen was reached from, when it was reached from
 * a row of the in-force view.
 */
class ExchangeRateHistoryViewModel(
    initialCurrency: String? = null,
    exchangeRateRepository: IExchangeRateRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(ExchangeRateHistoryFilters(currency = initialCurrency))

    val uiState = combine(
        exchangeRateRepository.observeAll(),
        filters,
    ) { rates, filters ->
        val staleBefore = today().minus(OUTDATED_AFTER_DAYS, DateTimeUnit.DAY)

        ExchangeRateHistoryUiState(
            groups = rates
                .filter { filters.accepts(it) }
                .map { ExchangeRateHistoryItem(rate = it, isOutdated = it.date < staleBefore) }
                .groupBy { it.rate.counterCurrency }
                .map { (counterCurrency, items) ->
                    ExchangeRateHistoryGroup(
                        counterCurrency = counterCurrency,
                        rates = items.sortedByDescending { it.rate.date },
                    )
                }
                .sortedByDescending { group -> group.rates.first().rate.date },
            filters = filters,
            // Offered out of the **whole** archive and not out of what is on screen: a
            // filter that only offers what survives the current one cannot be widened.
            currencies = rates
                .flatMap { listOf(it.currency, it.counterCurrency) }
                .distinct()
                .sorted(),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExchangeRateHistoryUiState(filters = filters.value),
    )

    fun onFilterByDate(start: LocalDate?, end: LocalDate?) {
        filters.update { it.copy(start = start, end = end) }
    }

    fun onFilterByCurrency(currency: String?) {
        filters.update { it.copy(currency = currency) }
    }

    fun onFilterBySource(source: ExchangeRate.Source?) {
        filters.update { it.copy(source = source) }
    }

    fun onClearFilters() {
        filters.value = ExchangeRateHistoryFilters()
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    companion object {
        /**
         * Not derivable from the domain — an opinion about volatility, and the same one
         * the in-force view states.
         */
        const val OUTDATED_AFTER_DAYS = 30
    }
}

/** The three filters compose by conjunction; an unset one narrows nothing. */
private fun ExchangeRateHistoryFilters.accepts(rate: ExchangeRate): Boolean {
    if (start != null && rate.date < start) return false
    if (end != null && rate.date > end) return false
    // Either end: the dollar priced in reais and the real priced in dollars are both
    // observations *about the dollar*, and hiding one of them would hide a row the user
    // came here to remove.
    if (currency != null && rate.currency != currency && rate.counterCurrency != currency) return false
    if (source != null && rate.source != source) return false
    return true
}
