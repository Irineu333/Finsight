package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class AdvancePaymentAction {
    data class SelectAccount(val account: Account?) : AdvancePaymentAction()

    /** What is in the card-currency field and in the date field, as they are typed. */
    data class AmountChanged(val amount: Double) : AdvancePaymentAction()
    data class DateChanged(val date: LocalDate) : AdvancePaymentAction()

    data class Submit(
        /** What the card receives, in the card's currency — the ceiling applies to this. */
        val amount: Double,
        val date: LocalDate,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
        /**
         * What leaves the paying account, when the payment crosses currencies. `null` says
         * the operation is single-currency and no second field was shown.
         */
        val accountAmount: Double?,
    ) : AdvancePaymentAction()
}
