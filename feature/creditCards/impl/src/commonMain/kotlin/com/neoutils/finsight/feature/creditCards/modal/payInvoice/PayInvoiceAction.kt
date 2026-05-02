package com.neoutils.finsight.feature.creditCards.modal.payInvoice

import com.neoutils.finsight.core.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class PayInvoiceAction {
    data class SelectAccount(val account: Account?) : PayInvoiceAction()
    data class Submit(
        val date: LocalDate,
        val account: Account? = null,
    ) : PayInvoiceAction()
}
