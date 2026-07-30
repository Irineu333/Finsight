package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.component.Modal
import kotlinx.datetime.LocalDate

interface CreditCardsEntry {
    fun creditCardFormModal(creditCard: CreditCard? = null): Modal
    /**
     * [currentBillAmount] arrives denominated: what the invoice owes is the card's
     * money (design D17), and both sheets have to render it and bound the payment by
     * it. Passing the bare number would leave the sheet to guess the currency, which
     * is the one thing `DisplayAmount` exists to make impossible.
     */
    fun payInvoiceModal(invoice: Invoice, currentBillAmount: DisplayAmount): Modal
    fun advancePaymentModal(invoice: Invoice, currentBillAmount: DisplayAmount): Modal
    fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal
    fun editInvoiceBalanceModal(invoice: Invoice): Modal
}
