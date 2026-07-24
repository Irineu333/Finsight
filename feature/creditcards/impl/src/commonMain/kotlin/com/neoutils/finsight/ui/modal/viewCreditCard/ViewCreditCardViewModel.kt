package com.neoutils.finsight.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.ui.model.toArchivedUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewCreditCardViewModel(
    private val cardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    invoiceRepository: IInvoiceRepository,
    private val unarchiveCreditCard: UnarchiveCreditCardUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewCreditCardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        creditCardRepository.observeCreditCardById(cardId)
            .interceptAbsence(
                onMissing = { crashlytics.recordException(DetailNotFoundException("CreditCard", cardId)) },
                onDisappeared = { _events.send(ViewCreditCardEvent.Dismiss) },
            ),
        invoiceRepository.observeInvoicesByCreditCard(cardId),
    ) { creditCard, invoices ->
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
    // back in circulation there is nothing left to show — dismiss it. The use case
    // takes the domain CreditCard, resolved by id at the moment of the action so the
    // domain model never has to sit in observable state the UI could read.
    private fun unarchive() {
        viewModelScope.launch {
            val creditCard = creditCardRepository.getCreditCardById(cardId) ?: return@launch
            unarchiveCreditCard(creditCard)
                .onRight { _events.send(ViewCreditCardEvent.Dismiss) }
                .onLeft { crashlytics.recordException(it) }
        }
    }
}
