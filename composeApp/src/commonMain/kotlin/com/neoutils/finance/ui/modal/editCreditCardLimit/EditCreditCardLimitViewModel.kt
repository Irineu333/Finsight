@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editCreditCardLimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.GetOrCreateCurrentInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class EditCreditCardLimitViewModel(
    private val creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
    private val getOrCreateCurrentInvoiceUseCase: GetOrCreateCurrentInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditCreditCardLimitUiState())
    val uiState: StateFlow<EditCreditCardLimitUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            if (creditCard == null) {
                modalManager.dismiss()
                return@launch
            }

            val invoice = getOrCreateCurrentInvoiceUseCase(creditCardId)
            if (invoice == null) {
                modalManager.dismiss()
                return@launch
            }
            
            val transactions = transactionRepository.observeAllTransactions().first()
            val billAmount = calculateCreditCardBillUseCase(
                invoiceId = invoice.id,
                transactions = transactions
            )

            _uiState.value = EditCreditCardLimitUiState(
                currentLimit = creditCard.limit,
                currentBill = billAmount,
                isLoading = false
            )
        }
    }

    fun saveLimit(limit: Double) {
        viewModelScope.launch {
            val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            if (creditCard != null) {
                creditCardRepository.update(creditCard.copy(limit = limit))
            }
            modalManager.dismiss()
        }
    }
}

data class EditCreditCardLimitUiState(
    val currentLimit: Double = 0.0,
    val currentBill: Double = 0.0,
    val isLoading: Boolean = true
)

