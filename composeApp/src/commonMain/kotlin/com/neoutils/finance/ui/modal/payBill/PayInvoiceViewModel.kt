@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.payBill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class PayInvoiceViewModel(
    private val invoiceId: Long,
    private val payInvoicePaymentUseCase: PayInvoicePaymentUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun payInvoice(
        date: LocalDate
    ) = viewModelScope.launch {

        val invoiceAmount = calculateInvoiceUseCase(invoiceId)

        if (invoiceAmount == 0.0) {
            payInvoiceUseCase(
                invoiceId = invoiceId,
                paidAt = date,
            )
        } else {
            payInvoicePaymentUseCase(
                invoiceId = invoiceId,
                date = date
            )
        }.onSuccess {
            modalManager.dismissAll()
        }
    }
}
