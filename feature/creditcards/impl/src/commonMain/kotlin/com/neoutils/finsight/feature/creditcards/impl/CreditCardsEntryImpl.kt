package com.neoutils.finsight.feature.creditcards.impl

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import kotlinx.datetime.LocalDate

internal class CreditCardsEntryImpl : CreditCardsEntry {
    override fun creditCardFormModal(creditCard: CreditCard?): Modal = CreditCardFormModal(creditCard)
    override fun payInvoiceModal(invoice: Invoice, currentBillAmount: Double): Modal =
        PayInvoiceModal(invoice, currentBillAmount)
    override fun advancePaymentModal(invoice: Invoice, currentBillAmount: Double): Modal =
        AdvancePaymentModal(invoice, currentBillAmount)
    override fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal =
        CloseInvoiceModal(invoiceId, closingDate)
    override fun editInvoiceBalanceModal(invoice: Invoice): Modal =
        EditInvoiceBalanceModal(invoice)
}
