@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.domain.usecase.GetCurrentInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ViewCreditCardViewModel(
        private val creditCard: CreditCard,
        private val initialBillAmount: Double,
        private val creditCardRepository: ICreditCardRepository,
        private val transactionRepository: ITransactionRepository,
        private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
        private val getCurrentInvoiceUseCase: GetCurrentInvoiceUseCase,
        private val closeInvoiceUseCase: CloseInvoiceUseCase,
        private val payInvoiceUseCase: PayInvoiceUseCase
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
            val invoice = getCurrentInvoiceUseCase(creditCard.id) ?: return@launch
            val transactions = transactionRepository.observeAllTransactions().first()
            val billAmount =
                    calculateCreditCardBillUseCase(
                            invoiceId = invoice.id,
                            transactions = transactions
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

    fun closeInvoice() {
        viewModelScope.launch {
            val invoice = _uiState.value.currentInvoice ?: return@launch
            val closedAt = Clock.System.now().toEpochMilliseconds()
            closeInvoiceUseCase(invoice.id, closedAt)
            refreshBill()
        }
    }

    fun payInvoice() {
        viewModelScope.launch {
            val invoice = _uiState.value.currentInvoice ?: return@launch
            val paidAt = Clock.System.now().toEpochMilliseconds()
            payInvoiceUseCase(invoice.id, paidAt)
            refreshBill()
        }
    }
}

data class ViewCreditCardUiState(
        val creditCard: CreditCard,
        val billAmount: Double,
        val currentInvoice: Invoice? = null
)
