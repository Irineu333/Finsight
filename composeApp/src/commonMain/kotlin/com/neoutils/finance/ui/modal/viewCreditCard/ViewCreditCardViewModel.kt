@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCreditCardViewModel(
    private val creditCard: CreditCard,
    private val initialBillAmount: Double,
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase
) : ViewModel() {

    private val currentMonth get() = Clock.System.now().toYearMonth()

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
            val transactions = transactionRepository.observeAllTransactions().first()
            val billAmount = calculateCreditCardBillUseCase(
                creditCardId = creditCard.id,
                target = currentMonth,
                transactions = transactions
            )

            val currentCard = creditCardRepository.getCreditCardById(creditCard.id) ?: creditCard

            _uiState.value = _uiState.value.copy(
                creditCard = currentCard,
                billAmount = billAmount
            )
        }
    }
}

data class ViewCreditCardUiState(
    val creditCard: CreditCard,
    val billAmount: Double
)

