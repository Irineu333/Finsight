package com.neoutils.finance.ui.modal.advancePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class AdvancePaymentViewModel(
    private val invoiceId: Long,
    private val advanceInvoicePaymentUseCase: AdvanceInvoicePaymentUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun advancePayment(
        amount: Double,
        date: LocalDate
    ) = viewModelScope.launch {
        advanceInvoicePaymentUseCase(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
        ).onSuccess {
            modalManager.dismissAll()
        }
    }
}

