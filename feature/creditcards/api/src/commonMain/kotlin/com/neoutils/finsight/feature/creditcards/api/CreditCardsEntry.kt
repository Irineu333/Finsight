package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.component.Modal
import kotlinx.datetime.LocalDate

interface CreditCardsEntry {
    fun creditCardFormModal(creditCard: CreditCard? = null): Modal
    fun payInvoiceModal(invoice: Invoice, currentBillAmount: Double): Modal
    fun advancePaymentModal(invoice: Invoice, currentBillAmount: Double): Modal
    fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal
    fun editInvoiceBalanceModal(invoice: Invoice): Modal
}
