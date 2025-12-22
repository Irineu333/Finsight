package com.neoutils.finance.ui.modal.editCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalTime::class)
class EditCreditCardViewModel(
    private val creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditCreditCardUiState())
    val uiState: StateFlow<EditCreditCardUiState> = _uiState.asStateFlow()

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

            val invoice = invoiceRepository.getLatestUnpaidInvoice(creditCardId)
            val billAmount =
                if (invoice != null) {
                    calculateInvoiceUseCase(
                        invoiceId = invoice.id,
                    )
                } else {
                    0.0
                }

            _uiState.value =
                EditCreditCardUiState(
                    currentName = creditCard.name,
                    currentLimit = creditCard.limit,
                    currentClosingDay = creditCard.closingDay,
                    currentBill = billAmount,
                    isLoading = false
                )
        }
    }

    fun save(name: String, limit: Double, closingDay: Int?) {
        viewModelScope.launch {
            val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            if (creditCard != null) {
                creditCardRepository.update(
                    creditCard.copy(name = name.trim(), limit = limit, closingDay = closingDay)
                )
            }
            modalManager.dismiss()
        }
    }
}

data class EditCreditCardUiState(
    val currentName: String = "",
    val currentLimit: Double = 0.0,
    val currentClosingDay: Int? = null,
    val currentBill: Double = 0.0,
    val isLoading: Boolean = true
)
