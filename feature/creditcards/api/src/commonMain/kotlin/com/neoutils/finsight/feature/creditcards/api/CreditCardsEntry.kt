package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.component.Modal
import kotlinx.datetime.LocalDate

interface CreditCardsEntry {
    fun creditCardFormModal(creditCard: CreditCard? = null): Modal

    /**
     * Paying an invoice — one way in, whatever state the invoice is in; the state
     * decides the mode, and nothing else does.
     *
     * [invoiceId] is a **pre-selection**: opened from an invoice in view it names that
     * one, and opened without context the sheet lets the user choose. What is owed is
     * not a parameter, because the sheet reads it from whichever invoice is selected —
     * a figure passed in would describe another invoice the moment the user switches.
     */
    fun invoicePaymentModal(invoiceId: Long? = null): Modal

    fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal
    fun editInvoiceBalanceModal(invoice: Invoice): Modal
}
