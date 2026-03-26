package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class AdvancePaymentAction {
    data class SelectAccount(val account: Account?) : AdvancePaymentAction()
    data class Submit(
        val amount: Double,
        val date: LocalDate,
        val account: Account? = null,
    ) : AdvancePaymentAction()
}
