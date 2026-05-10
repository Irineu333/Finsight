package com.neoutils.finsight.feature.creditCards.modal.payInvoice

import com.neoutils.finsight.feature.accounts.model.Account
import kotlinx.datetime.LocalDate

sealed class PayInvoiceAction {
    data class SelectAccount(val account: Account?) : PayInvoiceAction()
    data class SelectDate(val date: LocalDate) : PayInvoiceAction()
    data object Submit : PayInvoiceAction()
}
