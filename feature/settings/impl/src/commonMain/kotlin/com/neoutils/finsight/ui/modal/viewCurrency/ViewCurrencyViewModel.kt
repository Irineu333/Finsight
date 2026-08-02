package com.neoutils.finsight.ui.modal.viewCurrency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.ui.model.retireActionOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One currency, and what may be done to it.
 *
 * It observes the registry rather than receiving a row: editing the symbol from here
 * has to be visible without reopening, and a currency deleted from this very screen has
 * to close it instead of leaving a sheet describing something that no longer exists.
 */
class ViewCurrencyViewModel(
    private val code: String,
    currencyRepository: ICurrencyRepository,
    baseCurrencyRepository: IBaseCurrencyRepository,
    private val deleteCurrency: DeleteCurrencyUseCase,
    private val archiveCurrency: ArchiveCurrencyUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewCurrencyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        currencyRepository.observeAll()
            .map { all -> all.firstOrNull { it.code == code } }
            // Opened on a code that is not a row is a defect worth reporting; the row
            // *disappearing* is the normal way out of this screen — deleting from here —
            // and closes it instead.
            .interceptAbsence(
                onMissing = { crashlytics.recordException(DetailNotFoundException("Currency", code)) },
                onDisappeared = { _events.send(ViewCurrencyEvent.Dismiss) },
            ),
        currencyRepository.observeOffered().map { offered -> offered.map { it.code }.toSet() },
        baseCurrencyRepository.observe(),
    ) { currency, offeredCodes, base ->
        currency ?: return@combine ViewCurrencyUiState.Error

        val usage = deleteCurrency.usageOf(currency.code)

        ViewCurrencyUiState.Content(
            currency = currency,
            isArchived = currency.code !in offeredCodes,
            isBase = currency.code == base,
            usage = usage,
            // The rule has one owner, in the use case. This maps its answer to how the
            // action is named and drawn, and never decides it again — except for the
            // base, which is offered no retirement at all.
            retireAction = when (currency.code) {
                base -> null
                else -> retireActionOf(mustPreserve = !usage.isDeletable)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCurrencyUiState.Loading,
    )

    fun onAction(action: ViewCurrencyAction) {
        when (action) {
            // Reversible and innocuous, so no confirmation — the same call archiving a
            // category already makes. The screen observes the registry, so the button
            // swaps back on its own.
            ViewCurrencyAction.Unarchive -> viewModelScope.launch {
                archiveCurrency.unarchive(code)
            }
        }
    }
}
