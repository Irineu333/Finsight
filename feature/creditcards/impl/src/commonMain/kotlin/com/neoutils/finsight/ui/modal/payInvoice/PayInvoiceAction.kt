package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class PayInvoiceAction {
    data class SelectAccount(val account: Account?) : PayInvoiceAction()

    /** The date as it is typed: which quote governs the payment depends on it. */
    data class DateChanged(val date: LocalDate) : PayInvoiceAction()

    data class Submit(
        val date: LocalDate,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
        /**
         * What leaves the paying account, when the payment crosses currencies. `null` says
         * the operation is single-currency and no second field was shown — the amount is
         * then the debt itself, which only the domain may state.
         */
        val accountAmount: Double?,
    ) : PayInvoiceAction()
}
