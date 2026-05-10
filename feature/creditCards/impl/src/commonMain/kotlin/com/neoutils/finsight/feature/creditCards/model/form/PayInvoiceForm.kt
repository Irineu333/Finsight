package com.neoutils.finsight.feature.creditCards.model.form

import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import kotlinx.datetime.LocalDate

data class PayInvoiceForm(
    val invoice: Invoice?,
    val creditCard: CreditCard?,
    val account: Account?,
    val date: LocalDate,
    val currentBillAmount: Double,
) {
    val outstandingDebt: Double get() = if (currentBillAmount < 0.0) -currentBillAmount else currentBillAmount

    val minDate = invoice?.let {
        creditCard?.let {
            invoice.closingMonth.safeOnDay(creditCard.closingDay)
        }
    }

    val maxDate = invoice?.let {
        creditCard?.let {
            invoice.dueMonth.safeOnDay(creditCard.dueDay)
        }
    }

    fun isValid(): Boolean {
        if (invoice == null) return false
        if (creditCard == null) return false
        if (account == null) return false
        if (outstandingDebt <= 0.0) return false

        val maxDate = maxDate ?: return false
        val minDate = minDate ?: return false

        return date in minDate..maxDate
    }
}
