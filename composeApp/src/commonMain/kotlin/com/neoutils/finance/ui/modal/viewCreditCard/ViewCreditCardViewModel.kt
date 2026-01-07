@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class ViewCreditCardViewModel(
    creditCard: CreditCard,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: com.neoutils.finance.domain.repository.ITransactionRepository,
    private val invoiceUiMapper: InvoiceUiMapper,
) : ViewModel() {

    private val creditCardFlow = creditCardRepository
        .observeCreditCardById(creditCard.id)
        .filterNotNull()

    private val invoiceFlow = invoiceRepository
        .observeUnpaidInvoice(creditCard.id)

    private val transactionsFlow = invoiceFlow.flatMapMerge { invoice ->
        if (invoice == null) {
            return@flatMapMerge flowOf()
        }

        transactionRepository.observeTransactionsBy(
            invoiceId = invoice.id
        )
    }

    val uiState = combine(
        creditCardFlow,
        invoiceFlow,
        transactionsFlow,
    ) { creditCard, invoice, _ ->
        ViewCreditCardUiState(
            creditCard = creditCard,
            invoiceUi = invoice?.let {
                invoiceUiMapper.toUi(
                    invoice = it,
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCreditCardUiState(
            creditCard = creditCard,
            invoiceUi = null,
        )
    )
}
