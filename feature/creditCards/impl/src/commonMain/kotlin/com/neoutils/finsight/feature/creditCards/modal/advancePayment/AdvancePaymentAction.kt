package com.neoutils.finsight.feature.creditCards.modal.advancePayment

import com.neoutils.finsight.feature.accounts.model.Account
import kotlinx.datetime.LocalDate

sealed class AdvancePaymentAction {
    data class SelectAccount(
        val account: Account?
    ) : AdvancePaymentAction()

    data class SelectDate(
        val date: LocalDate
    ) : AdvancePaymentAction()

    data class Submit(
        val amount: Double
    ) : AdvancePaymentAction()
}
