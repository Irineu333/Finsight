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
import com.neoutils.finance.domain.usecase.GetOrCreateCurrentInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class ViewCreditCardViewModel(
    private val creditCard: CreditCard,
    private val initialBillAmount: Double,
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
    private val getOrCreateCurrentInvoiceUseCase: GetOrCreateCurrentInvoiceUseCase,
    private val closeInvoiceUseCase: CloseInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ViewCreditCardUiState(
            creditCard = creditCard,
            billAmount = initialBillAmount
        )
    )
    val uiState: StateFlow<ViewCreditCardUiState> = _uiState.asStateFlow()

    init {
        refreshBill()
    }

    private fun refreshBill() {
        viewModelScope.launch {
            // Se o cartão foi deletado, o UseCase retorna null
            val invoice = getOrCreateCurrentInvoiceUseCase(creditCard.id) ?: return@launch
            val transactions = transactionRepository.observeAllTransactions().first()
            val billAmount = calculateCreditCardBillUseCase(
                invoiceId = invoice.id,
                transactions = transactions
            )

            val currentCard = creditCardRepository.getCreditCardById(creditCard.id) ?: creditCard

            _uiState.value = _uiState.value.copy(
                creditCard = currentCard,
                billAmount = billAmount,
                currentInvoice = invoice
            )
        }
    }

    fun closeInvoice() {
        viewModelScope.launch {
            val invoice = _uiState.value.currentInvoice ?: return@launch
            closeInvoiceUseCase(invoice.id)
            refreshBill()
        }
    }

    fun payInvoice() {
        viewModelScope.launch {
            val invoice = _uiState.value.currentInvoice ?: return@launch
            payInvoiceUseCase(invoice.id)
            refreshBill()
        }
    }
}

data class ViewCreditCardUiState(
    val creditCard: CreditCard,
    val billAmount: Double,
    val currentInvoice: Invoice? = null
)

