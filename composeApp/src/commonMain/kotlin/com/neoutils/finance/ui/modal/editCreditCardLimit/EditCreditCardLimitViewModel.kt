package com.neoutils.finance.ui.modal.editCreditCardLimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditCreditCardLimitViewModel(
    private val creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    val uiState = combine(
        creditCardRepository.observeCreditCardById(creditCardId).filterNotNull(),
        invoiceRepository.observeOpenInvoice(creditCardId).filterNotNull()
    ) { creditCard, invoice ->
        EditCreditCardLimitUiState(
            currentLimit = creditCard.limit,
            currentBill = calculateInvoiceUseCase(
                invoiceId = invoice.id
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditCreditCardLimitUiState()
    )

    fun updateLimit(limit: Double) = viewModelScope.launch {
        updateCreditCardUseCase(creditCardId) {
            it.copy(limit = limit)
        }.onSuccess {
            modalManager.dismiss()
        }
    }
}
