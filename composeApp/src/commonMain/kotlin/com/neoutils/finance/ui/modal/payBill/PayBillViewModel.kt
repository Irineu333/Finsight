@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.payBill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.PayCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

class PayBillViewModel(
    private val invoiceId: Long,
    private val payBillUseCase: PayCreditCardBillUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun payBill(
        amount: Double,
        date: LocalDate
    ) {
        viewModelScope.launch {
            // 1. Criar transação de pagamento (apenas se valor > 0)
            if (amount > 0) {
                payBillUseCase(
                    invoiceId = invoiceId,
                    amount = amount,
                    date = date
                )
            }

            // 2. Marcar fatura como PAGA
            payInvoiceUseCase(invoiceId)

            modalManager.dismissAll()
        }
    }
}


