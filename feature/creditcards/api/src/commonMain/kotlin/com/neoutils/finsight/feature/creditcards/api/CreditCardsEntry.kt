package com.neoutils.finsight.feature.creditcards.api

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
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

    /**
     * The same form, correcting a partial payment already registered.
     *
     * It is a member of its own rather than a nullable parameter on the one above: a
     * single member covering both modes would have to take everything as nullable and
     * would accept states that mean nothing — an invoice to pre-select *and* an
     * operation to correct, or neither.
     *
     * Only the correction crosses this boundary from outside the feature. Registering a
     * payment is born on the card surfaces, inside the module that owns the form.
     */
    fun editInvoicePaymentModal(transaction: Transaction): Modal

    fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal
    fun editInvoiceBalanceModal(invoice: Invoice): Modal
}
