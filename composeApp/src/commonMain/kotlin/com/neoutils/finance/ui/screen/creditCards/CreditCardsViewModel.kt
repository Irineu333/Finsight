@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.creditCards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.mapper.CreditCardBillUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
    private val creditCardBillUiMapper: CreditCardBillUiMapper
) : ViewModel() {

    private val currentMonth get() = Clock.System.now().toYearMonth()

    val uiState = combine(
        creditCardRepository.getAllCreditCards(),
        transactionRepository.observeAllTransactions()
    ) { creditCards, transactions ->
        CreditCardsUiState(
            creditCards = creditCards.map { creditCard ->
                val billAmount = calculateCreditCardBillUseCase(
                    creditCardId = creditCard.id,
                    target = currentMonth,
                    transactions = transactions
                )
                CreditCardWithBill(
                    creditCard = creditCard,
                    billUi = creditCardBillUiMapper.toUi(
                        bill = billAmount,
                        limit = creditCard.limit
                    ),
                    billAmount = billAmount
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreditCardsUiState()
    )
}
