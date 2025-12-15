@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editInvoicePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

class EditInvoicePaymentViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun updateInvoicePayment(
        amount: Double,
        date: LocalDate
    ) {
        viewModelScope.launch {
            require(amount > 0) { "Payment amount must be positive" }
            
            val updatedTransaction = transaction.copy(
                amount = -amount,
                date = date
            )
            
            transactionRepository.update(updatedTransaction)
            modalManager.dismiss()
        }
    }
}
