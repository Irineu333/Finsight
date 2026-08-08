package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class PayInvoiceAction {
    data class SelectAccount(val account: Account?) : PayInvoiceAction()

    /** The day the payment is booked — which is the day whose rate the archive answers with. */
    data class ChangeDate(val date: LocalDate) : PayInvoiceAction()

    data class Submit(
        val date: LocalDate,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
        /**
         * What leaves the account, when it is denominated differently from the card.
         * What the invoice owes is not on this list, because it is not the user's to
         * state.
         */
        val paidAmount: Double,
    ) : PayInvoiceAction()
}
