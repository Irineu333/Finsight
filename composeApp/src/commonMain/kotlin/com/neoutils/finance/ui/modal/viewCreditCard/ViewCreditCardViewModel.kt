@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewCreditCardViewModel(
    private val creditCard: CreditCard,
    private val initialBillAmount: Double,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ViewCreditCardUiState(creditCard = creditCard, billAmount = initialBillAmount)
        )
    val uiState: StateFlow<ViewCreditCardUiState> = _uiState.asStateFlow()

    init {
        refreshBill()
    }

    private fun refreshBill() {
        viewModelScope.launch {
            val invoice = invoiceRepository.getLatestUnpaidInvoice(creditCard.id) ?: return@launch

            val billAmount = calculateInvoiceUseCase(
                invoiceId = invoice.id,
            )

            val currentCard = creditCardRepository.getCreditCardById(creditCard.id) ?: creditCard

            _uiState.value =
                _uiState.value.copy(
                    creditCard = currentCard,
                    billAmount = billAmount,
                    currentInvoice = invoice
                )
        }
    }
}

data class ViewCreditCardUiState(
    val creditCard: CreditCard,
    val billAmount: Double,
    val currentInvoice: Invoice? = null
)
