package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class AdvancePaymentAction {
    data class SelectAccount(val account: Account?) : AdvancePaymentAction()
    data class Submit(
        val amount: Double,
        val date: LocalDate,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
    ) : AdvancePaymentAction()
}
