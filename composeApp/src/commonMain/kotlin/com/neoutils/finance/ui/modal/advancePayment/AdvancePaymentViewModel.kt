package com.neoutils.finance.ui.modal.advancePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.usecase.PayCreditCardBillUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class AdvancePaymentViewModel(
    private val invoiceId: Long,
    private val payCreditCardBillUseCase: PayCreditCardBillUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun advancePayment(amount: Double, date: LocalDate) {
        viewModelScope.launch {
            payCreditCardBillUseCase(
                invoiceId = invoiceId,
                amount = amount,
                date = date,
                type = Transaction.Type.ADVANCE_PAYMENT,
                title = "Antecipação de Fatura"
            )
            modalManager.dismissAll()
        }
    }
}

