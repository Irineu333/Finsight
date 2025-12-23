package com.neoutils.finance.ui.modal.editInvoicePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class EditInvoicePaymentViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun updateInvoicePayment(
        amount: Double,
        date: LocalDate
    ) = viewModelScope.launch {
        require(amount > 0) { "Payment amount must be positive" }

        transactionRepository.update(
            transaction.copy(
                amount = -amount,
                date = date
            )
        )

        modalManager.dismiss()
    }
}
