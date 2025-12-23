package com.neoutils.finance.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class ViewCreditCardViewModel(
    creditCard: CreditCard,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: com.neoutils.finance.domain.repository.ITransactionRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) : ViewModel() {

    private val creditCardFlow = creditCardRepository
        .observeCreditCardById(creditCard.id)
        .filterNotNull()

    private val invoiceFlow = invoiceRepository
        .observeLatestUnpaidInvoice(creditCard.id)
        .filterNotNull()

    val uiState = combine(
        creditCardFlow,
        invoiceFlow,
        transactionRepository.observeAllTransactions()
    ) { creditCard, invoice, transactions ->
        ViewCreditCardUiState(
            creditCard = creditCard,
            invoiceAmount = calculateInvoiceUseCase(
                invoiceId = invoice.id,
                transactions = transactions
            ),
            currentInvoice = invoice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCreditCardUiState(
            creditCard = creditCard,
            invoiceAmount = 0.0, // TODO: improve this
        )
    )
}
