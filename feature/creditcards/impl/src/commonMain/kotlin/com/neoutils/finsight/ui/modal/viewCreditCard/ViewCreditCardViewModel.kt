package com.neoutils.finsight.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.ui.model.toArchivedUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewCreditCardViewModel(
    cardId: Long,
    creditCardRepository: ICreditCardRepository,
    invoiceRepository: IInvoiceRepository,
    private val unarchiveCreditCard: UnarchiveCreditCardUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewCreditCardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // The domain card stays here, on the ViewModel side of the boundary — the UI
    // reads the flat [ViewCreditCardUiState], never the domain graph. The unarchive
    // use case (which takes a CreditCard) reads it from here.
    private val currentCard = MutableStateFlow<CreditCard?>(null)

    val uiState = combine(
        creditCardRepository.observeCreditCardById(cardId)
            .interceptAbsence(
                onMissing = { crashlytics.recordException(DetailNotFoundException("CreditCard", cardId)) },
                onDisappeared = { _events.send(ViewCreditCardEvent.Dismiss) },
            ),
        invoiceRepository.observeInvoicesByCreditCard(cardId),
    ) { creditCard, invoices ->
        currentCard.value = creditCard
        creditCard ?: return@combine ViewCreditCardUiState.Error
        ViewCreditCardUiState.Content(
            card = creditCard.toArchivedUi(),
            isArchived = creditCard.isArchived,
            invoiceCount = invoices.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCreditCardUiState.Loading,
    )

    fun onAction(action: ViewCreditCardAction) {
        when (action) {
            ViewCreditCardAction.Unarchive -> unarchive()
        }
    }

    // Reversible and innocuous (design D8): no confirmation. This detail is
    // archived-only and reached solely from the archived list, so once the card is
    // back in circulation there is nothing left to show — dismiss it.
    private fun unarchive() {
        val creditCard = currentCard.value ?: return
        viewModelScope.launch {
            unarchiveCreditCard(creditCard)
                .onRight { _events.send(ViewCreditCardEvent.Dismiss) }
                .onLeft { crashlytics.recordException(it) }
        }
    }
}
