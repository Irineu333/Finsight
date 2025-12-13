@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.payBill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.PayCreditCardBillUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

class PayBillViewModel(
    private val creditCardId: Long,
    private val payBillUseCase: PayCreditCardBillUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun payBill(
        amount: Double,
        date: LocalDate
    ) {
        viewModelScope.launch {
            payBillUseCase(
                creditCardId = creditCardId,
                amount = amount,
                date = date
            )
            modalManager.dismiss()
        }
    }
}

