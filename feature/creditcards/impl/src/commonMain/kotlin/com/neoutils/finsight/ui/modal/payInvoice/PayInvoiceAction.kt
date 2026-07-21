package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class PayInvoiceAction {
    data class SelectAccount(val account: Account?) : PayInvoiceAction()
    data class Submit(
        val date: LocalDate,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
    ) : PayInvoiceAction()
}
