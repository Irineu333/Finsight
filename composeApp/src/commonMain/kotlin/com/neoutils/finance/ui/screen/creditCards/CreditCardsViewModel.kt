@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.screen.creditCards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.GetOrCreateCurrentInvoiceUseCase
import com.neoutils.finance.ui.mapper.CreditCardBillUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
    private val getOrCreateCurrentInvoiceUseCase: GetOrCreateCurrentInvoiceUseCase,
    private val creditCardBillUiMapper: CreditCardBillUiMapper
) : ViewModel() {

    val uiState = combine(
        creditCardRepository.observeAllCreditCards(),
        transactionRepository.observeAllTransactions()
    ) { creditCards, transactions ->
        creditCards to transactions
    }.flatMapLatest { (creditCards, transactions) ->
        flow {
            val creditCardWithBills = creditCards.mapNotNull { creditCard ->
                val invoice = getOrCreateCurrentInvoiceUseCase(creditCard.id)
                    ?: return@mapNotNull null
                val billAmount = calculateCreditCardBillUseCase(
                    invoiceId = invoice.id,
                    transactions = transactions
                )
                CreditCardWithBill(
                    creditCard = creditCard,
                    billUi = creditCardBillUiMapper.toUi(
                        bill = billAmount,
                        limit = creditCard.limit,
                        invoiceStatus = invoice.status
                    ),
                    billAmount = billAmount,
                    currentInvoice = invoice
                )
            }
            emit(CreditCardsUiState(creditCards = creditCardWithBills))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreditCardsUiState()
    )
}

