package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class AdvancePaymentAction {
    data class SelectAccount(val account: Account?) : AdvancePaymentAction()

    /**
     * How much of the invoice is being settled, in the **card's** currency, and the day
     * it is settled — what the archive needs in order to say what that costs in the
     * account's.
     */
    data class ChangeAmount(val amount: Double) : AdvancePaymentAction()
    data class ChangeDate(val date: LocalDate) : AdvancePaymentAction()

    data class Submit(
        val amount: Double,
        val date: LocalDate,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
        /** What leaves the account, when it is denominated differently from the card. */
        val paidAmount: Double,
    ) : AdvancePaymentAction()
}
